package studypilot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class QuizRepository {
    private final Path directory;

    public QuizRepository(Path dataDirectory) {
        directory = dataDirectory.resolve("quizzes");
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
}
