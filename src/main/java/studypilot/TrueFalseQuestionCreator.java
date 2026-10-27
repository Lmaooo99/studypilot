package studypilot;

public class TrueFalseQuestionCreator extends QuestionCreator {
    protected Question createQuestion(RawQuestion raw) {
        String answer = raw.getCorrectAnswer().trim();
        if (raw.getType() != QuestionType.TRUE_FALSE
                || (!answer.equalsIgnoreCase("true") && !answer.equalsIgnoreCase("false"))) {
            throw new IllegalArgumentException("A true/false answer must be true or false.");
        }
        return new TrueFalseQuestion(raw.getText(), raw.getConceptName(), Boolean.parseBoolean(answer));
    }
}
