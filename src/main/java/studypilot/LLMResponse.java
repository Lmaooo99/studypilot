package studypilot;

public class LLMResponse {
    private final String text;
    private final boolean success;
    private final String errorMessage;

    public LLMResponse(String text, boolean success, String errorMessage) {
        this.text = text;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getText() {
        return text;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
