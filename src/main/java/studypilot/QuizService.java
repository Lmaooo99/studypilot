package studypilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;

public class QuizService {
    private final Map<QuestionType, QuestionCreator> creators = new HashMap<>();
    private final LLMClient llmClient;
    private final PromptTemplates templates;
    private final ResponseParser parser;
    private final QuizRepository repository;
    private final RetrievalStrategy retrieval;
    private final ProgressTracker tracker;
    private final QuizGrader grader;

    public QuizService(LLMClient llmClient, PromptTemplates templates, ResponseParser parser,
                       QuizRepository repository, RetrievalStrategy retrieval, ProgressTracker tracker) {
        this.llmClient = llmClient;
        this.templates = templates;
        this.parser = parser;
        this.repository = repository;
        this.retrieval = retrieval;
        this.tracker = tracker;
        grader = new QuizGrader(llmClient, templates, parser);
        creators.put(QuestionType.MULTIPLE_CHOICE, new MCQQuestionCreator());
        creators.put(QuestionType.TRUE_FALSE, new TrueFalseQuestionCreator());
        creators.put(QuestionType.SHORT_ANSWER, new ShortAnswerQuestionCreator());
    }

    public Quiz createQuiz(Course course, String topic, int count, Difficulty difficulty, List<QuestionType> types) {
        validateRequest(topic, count, difficulty, types);
        List<Chunk> chunks = retrieval.retrieve(topic, course.getChunks(), 5);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("No relevant material was found. Import notes or choose a topic from them.");
        }
        Map<QuestionType, Integer> targets = questionTargets(count, types);
        List<Question> questions = new ArrayList<>();
        for (int attempt = 0; attempt < 3 && questions.size() < count; attempt++) {
            List<QuestionType> neededTypes = neededTypes(types, targets, questions);
            String prompt = templates.quizPrompt(topic, count - questions.size(), difficulty, neededTypes, chunks);
            for (Question question : questions) {
                prompt += "\nDo not repeat this accepted question: " + question.getText();
            }
            LLMResponse response = llmClient.generate(prompt);
            if (response.isSuccess()) {
                try {
                    addQuestions(parser.parseRawQuestions(response.getText()), questions, targets);
                } catch (JSONException e) {
                    // Keep accepted questions and request the missing ones in the next round.
                }
            }
        }
        if (questions.isEmpty()) {
            throw new IllegalStateException("No valid quiz questions were generated after two retries. No quiz was saved.");
        }
        Quiz quiz = new Quiz(course.getId(), topic, difficulty, questions);
        repository.saveQuiz(quiz);
        return quiz;
    }

    private void validateRequest(String topic, int count, Difficulty difficulty, List<QuestionType> types) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Enter a quiz topic.");
        }
        if (count < 1 || count > 50) {
            throw new IllegalArgumentException("Request between 1 and 50 quiz questions.");
        }
        if (difficulty == null || types == null || types.isEmpty()) {
            throw new IllegalArgumentException("Choose a difficulty and at least one question type.");
        }
        List<QuestionType> selected = new ArrayList<>();
        for (QuestionType type : types) {
            if (type == null || selected.contains(type)) {
                throw new IllegalArgumentException("Choose each question type at most once.");
            }
            selected.add(type);
        }
        if (count < types.size()) {
            throw new IllegalArgumentException("Request at least one question for each selected type.");
        }
    }

    private Map<QuestionType, Integer> questionTargets(int count, List<QuestionType> types) {
        Map<QuestionType, Integer> targets = new HashMap<>();
        for (int i = 0; i < types.size(); i++) {
            int target = count / types.size();
            if (i < count % types.size()) {
                target++;
            }
            targets.put(types.get(i), target);
        }
        return targets;
    }

    private int countType(List<Question> questions, QuestionType type) {
        int count = 0;
        for (Question question : questions) {
            if (question.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private List<QuestionType> neededTypes(List<QuestionType> types, Map<QuestionType, Integer> targets,
                                          List<Question> questions) {
        List<QuestionType> needed = new ArrayList<>();
        for (QuestionType type : types) {
            if (countType(questions, type) < targets.get(type)) {
                needed.add(type);
            }
        }
        return needed;
    }

    private void addQuestions(List<RawQuestion> proposed, List<Question> questions, Map<QuestionType, Integer> targets) {
        for (RawQuestion raw : proposed) {
            QuestionType type = raw.getType();
            if (!targets.containsKey(type) || countType(questions, type) >= targets.get(type)
                    || containsQuestion(questions, raw.getText())) {
                continue;
            }
            try {
                questions.add(creators.get(type).buildQuestion(raw));
            } catch (IllegalArgumentException e) {
                // Invalid answers, options or rubrics are discarded before saving.
            }
        }
    }

    private boolean containsQuestion(List<Question> questions, String text) {
        for (Question question : questions) {
            if (question.getText().equalsIgnoreCase(text.trim())) {
                return true;
            }
        }
        return false;
    }

    public Quiz getQuiz(String courseId, String quizId) {
        Quiz quiz = repository.loadQuiz(quizId);
        if (!quiz.getCourseId().equals(courseId)) {
            throw new IllegalArgumentException("Quiz not found in this course.");
        }
        return quiz;
    }

    public List<Quiz> listQuizzes(String courseId) {
        return repository.listQuizzes(courseId);
    }

    public QuizAttempt startAttempt(String courseId, String quizId) {
        Quiz quiz = getQuiz(courseId, quizId);
        QuizAttempt attempt = new QuizAttempt(quiz.getId(), courseId);
        repository.saveAttempt(attempt);
        return attempt;
    }

    public QuizAttempt getAttempt(String courseId, String attemptId) {
        QuizAttempt attempt = repository.loadAttempt(attemptId);
        if (!attempt.getCourseId().equals(courseId)) {
            throw new IllegalArgumentException("Quiz attempt not found in this course.");
        }
        return attempt;
    }

    public List<QuizAttempt> listAttempts(String courseId) {
        return repository.listAttempts(courseId);
    }

    public QuizAttempt submitAttempt(String courseId, String attemptId, Map<String, String> answers) {
        QuizAttempt attempt = getAttempt(courseId, attemptId);
        Quiz quiz = getQuiz(courseId, attempt.getQuizId());
        if (!attempt.isSubmitted()) {
            attempt.submit(checkedAnswers(quiz, answers));
            grader.grade(quiz, attempt);
        }
        // Retry progress/history persistence, without grading or replacing saved answers again.
        return saveResults(attempt);
    }

    private QuizAttempt saveResults(QuizAttempt attempt) {
        repository.saveAttempt(attempt);
        tracker.recordQuizAttempt(attempt);
        repository.saveAttempt(attempt);
        return attempt;
    }

    public QuizAttempt retryPendingGrading(String courseId, String attemptId) {
        QuizAttempt attempt = getAttempt(courseId, attemptId);
        if (!attempt.isSubmitted()) {
            throw new IllegalArgumentException("Submit the attempt before retrying grading.");
        }
        if (attempt.getPendingCount() == 0) {
            return saveResults(attempt);
        }
        Quiz quiz = getQuiz(courseId, attempt.getQuizId());
        grader.regradePending(quiz, attempt);
        return saveResults(attempt);
    }

    public ShortAnswerQuestion getPendingQuestion(String courseId, String attemptId, String questionId) {
        QuizAttempt attempt = getAttempt(courseId, attemptId);
        Quiz quiz = getQuiz(courseId, attempt.getQuizId());
        return pendingQuestion(quiz, attempt, questionId);
    }

    private ShortAnswerQuestion pendingQuestion(Quiz quiz, QuizAttempt attempt, String questionId) {
        if (!attempt.isSubmitted()) {
            throw new IllegalArgumentException("Submit the attempt before resolving pending answers.");
        }
        Question question = quiz.getQuestion(questionId);
        if (!(question instanceof ShortAnswerQuestion)
                || attempt.getResult(questionId).getStatus() != GradingStatus.PENDING) {
            throw new IllegalArgumentException("Only pending short answers can be self-graded.");
        }
        return (ShortAnswerQuestion) question;
    }

    public QuizAttempt selfGrade(String courseId, String attemptId, String questionId, boolean correct) {
        QuizAttempt attempt = getAttempt(courseId, attemptId);
        Quiz quiz = getQuiz(courseId, attempt.getQuizId());
        Question question = pendingQuestion(quiz, attempt, questionId);
        int points = 0;
        String explanation = "Student self-assessment: incorrect.";
        if (correct) {
            points = question.getPoints();
            explanation = "Student self-assessment: correct.";
        }
        attempt.replacePendingResult(new QuestionResult(question, GradingStatus.GRADED, points, explanation));
        return saveResults(attempt);
    }

    private Map<String, String> checkedAnswers(Quiz quiz, Map<String, String> answers) {
        if (answers == null) {
            throw new IllegalArgumentException("Supply answers for this attempt.");
        }
        for (String id : answers.keySet()) {
            quiz.getQuestion(id);
            if (answers.get(id) == null) {
                throw new IllegalArgumentException("Answer values must be text.");
            }
        }
        Map<String, String> checked = new HashMap<>();
        for (Question question : quiz.getQuestions()) {
            checked.put(question.getId(), answers.getOrDefault(question.getId(), "").trim());
        }
        return checked;
    }
}
