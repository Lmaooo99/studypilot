package studypilot;

import java.util.UUID;

public abstract class Question {
    private final String questionId;
    private final String text;
    private final String conceptName;
    private final int points;

    protected Question(String questionId, String text, String conceptName, int points) {
        if (!UUID.fromString(questionId).toString().equals(questionId)
                || text.isBlank() || conceptName.isBlank() || points < 1) {
            throw new IllegalArgumentException("Invalid question fields.");
        }
        this.questionId = questionId;
        this.text = text.trim();
        this.conceptName = conceptName.trim();
        this.points = points;
    }

    public String getId() {
        return questionId;
    }

    public String getText() {
        return text;
    }

    public String getConceptName() {
        return conceptName;
    }

    public int getPoints() {
        return points;
    }

    public abstract QuestionType getType();

    public abstract String getCorrectAnswer();
}
