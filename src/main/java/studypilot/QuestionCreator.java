package studypilot;

public abstract class QuestionCreator {
    public Question buildQuestion(RawQuestion raw) {
        if (raw == null || raw.getText().isBlank() || raw.getConceptName().isBlank()
                || raw.getCorrectAnswer().isBlank()) {
            throw new IllegalArgumentException("A question needs text, a topic and an answer.");
        }
        return createQuestion(raw);
    }

    protected abstract Question createQuestion(RawQuestion raw);
}
