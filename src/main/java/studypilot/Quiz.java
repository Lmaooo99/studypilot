package studypilot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Quiz {
    private final String quizId;
    private final String courseId;
    private final String topic;
    private final Difficulty difficulty;
    private final List<Question> questions;

    public Quiz(String courseId, String topic, Difficulty difficulty, List<Question> questions) {
        this(UUID.randomUUID().toString(), courseId, topic, difficulty, questions);
    }

    public Quiz(String quizId, String courseId, String topic, Difficulty difficulty, List<Question> questions) {
        if (!UUID.fromString(quizId).toString().equals(quizId)
                || !UUID.fromString(courseId).toString().equals(courseId)
                || topic.isBlank() || difficulty == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Invalid quiz fields.");
        }
        this.quizId = quizId;
        this.courseId = courseId;
        this.topic = topic.trim();
        this.difficulty = difficulty;
        this.questions = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Question question : questions) {
            if (question == null || ids.contains(question.getId())) {
                throw new IllegalArgumentException("Quiz questions must have different IDs.");
            }
            ids.add(question.getId());
            this.questions.add(question);
        }
    }

    public String getId() {
        return quizId;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getTopic() {
        return topic;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public List<Question> getQuestions() {
        return new ArrayList<>(questions);
    }

    public Question getQuestion(String questionId) {
        for (Question question : questions) {
            if (question.getId().equals(questionId)) {
                return question;
            }
        }
        throw new IllegalArgumentException("Question not found in this quiz.");
    }

    public String toString() {
        return topic + " [" + quizId.substring(0, 8) + "]";
    }

    public String describe() {
        StringBuilder text = new StringBuilder("Quiz ID: " + quizId + "\nTopic: " + topic
                + "\nDifficulty: " + difficulty + "\nQuestions: " + questions.size() + "\n");
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            text.append("\n" + (i + 1) + ". [" + question.getType() + "] " + question.getText() + "\n");
            if (question instanceof MultipleChoiceQuestion) {
                List<String> options = ((MultipleChoiceQuestion) question).getOptions();
                for (int j = 0; j < options.size(); j++) {
                    text.append("   " + (j + 1) + ") " + options.get(j) + "\n");
                }
            }
        }
        return text.toString();
    }
}
