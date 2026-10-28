package studypilot;

import java.util.HashMap;
import java.util.Map;

public class QuizGrader {
    private final Map<QuestionType, GradingStrategy> strategies = new HashMap<>();

    public QuizGrader(LLMClient llmClient, PromptTemplates templates, ResponseParser parser) {
        GradingStrategy exactMatch = new ExactMatchGrading();
        strategies.put(QuestionType.MULTIPLE_CHOICE, exactMatch);
        strategies.put(QuestionType.TRUE_FALSE, exactMatch);
        strategies.put(QuestionType.SHORT_ANSWER, new LLMRubricGrading(llmClient, templates, parser));
    }

    public void grade(Quiz quiz, QuizAttempt attempt) {
        checkAttempt(quiz, attempt);
        Map<String, String> answers = attempt.getAnswers();
        for (Question question : quiz.getQuestions()) {
            String answer = answers.getOrDefault(question.getId(), "");
            attempt.addResult(strategies.get(question.getType()).grade(question, answer));
        }
    }

    public void regradePending(Quiz quiz, QuizAttempt attempt) {
        checkAttempt(quiz, attempt);
        Map<String, String> answers = attempt.getAnswers();
        for (QuestionResult result : attempt.getResults()) {
            if (result.getStatus() == GradingStatus.PENDING) {
                Question question = quiz.getQuestion(result.getQuestionId());
                QuestionResult graded = strategies.get(question.getType()).grade(question,
                        answers.getOrDefault(question.getId(), ""));
                attempt.replacePendingResult(graded);
            }
        }
    }

    private void checkAttempt(Quiz quiz, QuizAttempt attempt) {
        if (!attempt.isSubmitted() || !quiz.getId().equals(attempt.getQuizId())
                || !quiz.getCourseId().equals(attempt.getCourseId())) {
            throw new IllegalArgumentException("A submitted attempt must belong to this quiz.");
        }
    }
}
