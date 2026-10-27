package studypilot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class QuizRepository {
    private final Path directory;
    private final Path attemptDirectory;

    public QuizRepository(Path dataDirectory) {
        directory = dataDirectory.resolve("quizzes");
        attemptDirectory = dataDirectory.resolve("attempts");
    }

    public void saveQuiz(Quiz quiz) {
        Path file = JsonFiles.namedFile(directory, quiz.getId());
        if (Files.exists(file)) {
            throw new IllegalStateException("Saved quizzes cannot be overwritten. Generate a new quiz instead.");
        }
        JSONObject data = new JSONObject();
        data.put("quizId", quiz.getId());
        data.put("courseId", quiz.getCourseId());
        data.put("topic", quiz.getTopic());
        data.put("difficulty", quiz.getDifficulty().name());
        JSONArray questions = new JSONArray();
        for (Question question : quiz.getQuestions()) {
            questions.put(questionToJson(question));
        }
        data.put("questions", questions);
        JsonFiles.write(file, data);
    }

    public Quiz loadQuiz(String quizId) {
        Path file = JsonFiles.namedFile(directory, quizId);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Quiz not found.");
        }
        JSONObject data = JsonFiles.read(file);
        try {
            if (!data.getString("quizId").equals(quizId)) {
                throw new JSONException("Quiz ID mismatch");
            }
            List<Question> questions = new ArrayList<>();
            JSONArray saved = data.getJSONArray("questions");
            for (int i = 0; i < saved.length(); i++) {
                questions.add(questionFromJson(saved.getJSONObject(i)));
            }
            return new Quiz(quizId, data.getString("courseId"), data.getString("topic"),
                    Difficulty.valueOf(data.getString("difficulty")), questions);
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("Saved quiz fields are invalid. The original file was not changed.");
        }
    }

    private JSONObject questionToJson(Question question) {
        JSONObject data = new JSONObject();
        data.put("questionId", question.getId());
        data.put("type", question.getType().name());
        data.put("text", question.getText());
        data.put("conceptName", question.getConceptName());
        data.put("points", question.getPoints());
        data.put("correctAnswer", question.getCorrectAnswer());
        if (question instanceof MultipleChoiceQuestion) {
            MultipleChoiceQuestion choice = (MultipleChoiceQuestion) question;
            data.put("options", new JSONArray(choice.getOptions()));
            data.put("correctIndex", choice.getCorrectIndex());
        } else if (question instanceof ShortAnswerQuestion) {
            data.put("rubric", ((ShortAnswerQuestion) question).getRubric());
        }
        return data;
    }

    private Question questionFromJson(JSONObject data) {
        String id = data.getString("questionId");
        String text = data.getString("text");
        String concept = data.getString("conceptName");
        int points = data.getInt("points");
        String answer = data.getString("correctAnswer");
        QuestionType type = QuestionType.valueOf(data.getString("type"));
        if (type == QuestionType.MULTIPLE_CHOICE) {
            List<String> options = new ArrayList<>();
            JSONArray saved = data.getJSONArray("options");
            for (int i = 0; i < saved.length(); i++) {
                options.add(saved.getString(i));
            }
            MultipleChoiceQuestion question = new MultipleChoiceQuestion(id, text, concept, points,
                    options, data.getInt("correctIndex"));
            if (!question.getCorrectAnswer().equals(answer)) {
                throw new JSONException("Correct option mismatch");
            }
            return question;
        } else if (type == QuestionType.TRUE_FALSE) {
            if (!answer.equals("true") && !answer.equals("false")) {
                throw new JSONException("Invalid true/false answer");
            }
            return new TrueFalseQuestion(id, text, concept, points, Boolean.parseBoolean(answer));
        }
        return new ShortAnswerQuestion(id, text, concept, points, answer, data.getString("rubric"));
    }

    public List<Quiz> listQuizzes(String courseId) {
        List<Quiz> quizzes = new ArrayList<>();
        if (!Files.exists(directory)) {
            return quizzes;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : entries) {
                files.add(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("The quiz directory could not be read.");
        }
        Collections.sort(files);
        for (Path file : files) {
            String name = file.getFileName().toString();
            Quiz quiz = loadQuiz(name.substring(0, name.length() - 5));
            if (quiz.getCourseId().equals(courseId)) {
                quizzes.add(quiz);
            }
        }
        return quizzes;
    }

    public void saveAttempt(QuizAttempt attempt) {
        Quiz quiz = loadQuiz(attempt.getQuizId());
        if (!quiz.getCourseId().equals(attempt.getCourseId())
                || (attempt.isSubmitted() && (attempt.getResults().size() != quiz.getQuestions().size()
                || attempt.getAnswers().size() != quiz.getQuestions().size()))) {
            throw new IllegalArgumentException("The attempt must contain the quiz's results and course.");
        }
        JSONObject data = new JSONObject();
        data.put("attemptId", attempt.getId());
        data.put("quizId", attempt.getQuizId());
        data.put("courseId", attempt.getCourseId());
        JSONObject answers = new JSONObject();
        for (Map.Entry<String, String> answer : attempt.getAnswers().entrySet()) {
            quiz.getQuestion(answer.getKey());
            answers.put(answer.getKey(), answer.getValue());
        }
        data.put("answers", answers);
        JSONArray results = new JSONArray();
        for (QuestionResult result : attempt.getResults()) {
            quiz.getQuestion(result.getQuestionId());
            results.put(resultToJson(result));
        }
        data.put("results", results);
        String time = "";
        if (attempt.isSubmitted()) {
            time = attempt.getSubmittedAt().toString();
        }
        data.put("submittedAt", time);
        JsonFiles.write(JsonFiles.namedFile(attemptDirectory, attempt.getId()), data);
    }

    private JSONObject resultToJson(QuestionResult result) {
        JSONObject data = new JSONObject();
        data.put("questionId", result.getQuestionId());
        data.put("conceptName", result.getConceptName());
        data.put("gradingStatus", result.getStatus().name());
        data.put("correct", result.isCorrect());
        data.put("pointsEarned", result.getPointsEarned());
        data.put("maxPoints", result.getMaxPoints());
        data.put("explanation", result.getExplanation());
        data.put("counted", result.isCounted());
        return data;
    }

    public QuizAttempt loadAttempt(String attemptId) {
        Path file = JsonFiles.namedFile(attemptDirectory, attemptId);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Quiz attempt not found.");
        }
        JSONObject data = JsonFiles.read(file);
        try {
            if (!data.getString("attemptId").equals(attemptId)) {
                throw new JSONException("Attempt ID mismatch");
            }
            Quiz quiz = loadQuiz(data.getString("quizId"));
            if (!quiz.getCourseId().equals(data.getString("courseId"))) {
                throw new JSONException("Attempt course mismatch");
            }
            Map<String, String> answers = new HashMap<>();
            JSONObject savedAnswers = data.getJSONObject("answers");
            for (String id : savedAnswers.keySet()) {
                quiz.getQuestion(id);
                answers.put(id, savedAnswers.getString(id));
            }
            List<QuestionResult> results = loadResults(data.getJSONArray("results"), quiz);
            LocalDateTime submittedAt = null;
            if (!data.getString("submittedAt").isEmpty()) {
                submittedAt = LocalDateTime.parse(data.getString("submittedAt"));
                if (answers.size() != quiz.getQuestions().size() || results.size() != quiz.getQuestions().size()) {
                    throw new JSONException("Incomplete submitted attempt");
                }
            }
            return new QuizAttempt(attemptId, quiz.getId(), quiz.getCourseId(), answers, results, submittedAt);
        } catch (JSONException | IllegalArgumentException | DateTimeException e) {
            throw new IllegalStateException("Saved attempt fields are invalid. The original file was not changed.");
        }
    }

    private List<QuestionResult> loadResults(JSONArray saved, Quiz quiz) {
        List<QuestionResult> results = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject data = saved.getJSONObject(i);
            Question question = quiz.getQuestion(data.getString("questionId"));
            if (ids.contains(question.getId())) {
                throw new JSONException("Repeated question result");
            }
            ids.add(question.getId());
            QuestionResult result = new QuestionResult(question, GradingStatus.valueOf(data.getString("gradingStatus")),
                    data.getInt("pointsEarned"), data.getString("explanation"), data.getBoolean("counted"));
            if (result.isCorrect() != data.getBoolean("correct") || result.getMaxPoints() != data.getInt("maxPoints")
                    || !result.getConceptName().equals(data.getString("conceptName"))) {
                throw new JSONException("Question result mismatch");
            }
            results.add(result);
        }
        return results;
    }

    public List<QuizAttempt> listAttempts(String courseId) {
        List<QuizAttempt> attempts = new ArrayList<>();
        if (!Files.exists(attemptDirectory)) {
            return attempts;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(attemptDirectory, "*.json")) {
            for (Path file : entries) {
                files.add(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("The attempt directory could not be read.");
        }
        Collections.sort(files);
        for (Path file : files) {
            String name = file.getFileName().toString();
            QuizAttempt attempt = loadAttempt(name.substring(0, name.length() - 5));
            if (attempt.getCourseId().equals(courseId)) {
                attempts.add(attempt);
            }
        }
        return attempts;
    }
}
