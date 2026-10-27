package studypilot;

public class ExactMatchGrading implements GradingStrategy {
    public QuestionResult grade(Question question, String answer) {
        String expected;
        String explanation = "Correct answer: ";
        if (question instanceof MultipleChoiceQuestion) {
            int optionNumber = ((MultipleChoiceQuestion) question).getCorrectIndex() + 1;
            expected = Integer.toString(optionNumber);
            explanation += optionNumber + ") " + question.getCorrectAnswer();
        } else if (question instanceof TrueFalseQuestion) {
            expected = question.getCorrectAnswer();
            explanation += expected;
        } else {
            throw new IllegalArgumentException("Exact matching is only for multiple-choice and true/false questions.");
        }
        if (answer.trim().equalsIgnoreCase(expected)) {
            return new QuestionResult(question, GradingStatus.GRADED, question.getPoints(), "Correct.");
        }
        if (answer.isBlank()) {
            explanation = "Unanswered. " + explanation;
        }
        return new QuestionResult(question, GradingStatus.GRADED, 0, explanation);
    }
}
