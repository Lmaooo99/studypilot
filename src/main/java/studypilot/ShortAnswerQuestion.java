package studypilot;

import java.util.UUID;

public class ShortAnswerQuestion extends Question {
    private final String modelAnswer;
    private final String rubric;

    public ShortAnswerQuestion(String text, String conceptName, String modelAnswer, String rubric) {
        this(UUID.randomUUID().toString(), text, conceptName, 1, modelAnswer, rubric);
    }

    public ShortAnswerQuestion(String id, String text, String conceptName, int points,
                               String modelAnswer, String rubric) {
        super(id, text, conceptName, points);
        if (modelAnswer.isBlank() || rubric.isBlank()) {
            throw new IllegalArgumentException("A short-answer question needs a model answer and rubric.");
        }
        this.modelAnswer = modelAnswer.trim();
        this.rubric = rubric.trim();
    }

    public String getRubric() {
        return rubric;
    }

    public QuestionType getType() {
        return QuestionType.SHORT_ANSWER;
    }

    public String getCorrectAnswer() {
        return modelAnswer;
    }
}
