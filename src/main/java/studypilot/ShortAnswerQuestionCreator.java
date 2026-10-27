package studypilot;

public class ShortAnswerQuestionCreator extends QuestionCreator {
    protected Question createQuestion(RawQuestion raw) {
        if (raw.getType() != QuestionType.SHORT_ANSWER) {
            throw new IllegalArgumentException("Expected a short-answer question.");
        }
        return new ShortAnswerQuestion(raw.getText(), raw.getConceptName(), raw.getCorrectAnswer(), raw.getRubric());
    }
}
