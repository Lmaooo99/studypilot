package studypilot;

import java.util.UUID;

public class TrueFalseQuestion extends Question {
    private final boolean correctAnswer;

    public TrueFalseQuestion(String text, String conceptName, boolean correctAnswer) {
        this(UUID.randomUUID().toString(), text, conceptName, 1, correctAnswer);
    }

    public TrueFalseQuestion(String id, String text, String conceptName, int points, boolean correctAnswer) {
        super(id, text, conceptName, points);
        this.correctAnswer = correctAnswer;
    }

    public QuestionType getType() {
        return QuestionType.TRUE_FALSE;
    }

    public String getCorrectAnswer() {
        return Boolean.toString(correctAnswer);
    }
}
