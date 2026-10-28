package studypilot;

import org.json.JSONException;

public class LLMRubricGrading implements GradingStrategy {
    private final LLMClient llmClient;
    private final PromptTemplates templates;
    private final ResponseParser parser;

    public LLMRubricGrading(LLMClient llmClient, PromptTemplates templates, ResponseParser parser) {
        this.llmClient = llmClient;
        this.templates = templates;
        this.parser = parser;
    }

    public QuestionResult grade(Question question, String answer) {
        if (!(question instanceof ShortAnswerQuestion)) {
            throw new IllegalArgumentException("Rubric grading is only for short-answer questions.");
        }
        if (answer.isBlank()) {
            return new QuestionResult(question, GradingStatus.GRADED, 0, "Unanswered.");
        }
        ShortAnswerQuestion shortAnswer = (ShortAnswerQuestion) question;
        String details = question.getText() + "\nModel answer: " + question.getCorrectAnswer()
                + "\nMaximum points: " + question.getPoints();
        LLMResponse response = llmClient.generate(templates.gradingPrompt(details, shortAnswer.getRubric(), answer));
        if (response.isSuccess()) {
            try {
                return parser.parseGrading(response.getText(), question);
            } catch (JSONException | IllegalArgumentException e) {
                return new QuestionResult(question, GradingStatus.PENDING, 0,
                        "Automatic grading returned invalid data. Retry grading or self-grade this answer.");
            }
        }
        return new QuestionResult(question, GradingStatus.PENDING, 0,
                "Automatic grading could not complete. Check API settings, retry, or self-grade this answer.");
    }
}
