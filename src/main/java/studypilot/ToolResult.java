package studypilot;

import java.util.HashMap;
import java.util.Map;

public class ToolResult {
    private final ResultStatus status;
    private final String output;
    private final String errorMessage;
    private final Map<String, String> data;
    private final boolean retryable;

    public ToolResult(ResultStatus status, String output, String errorMessage) {
        this(status, output, errorMessage, new HashMap<String, String>());
    }

    public ToolResult(ResultStatus status, String output, String errorMessage, Map<String, String> data) {
        this(status, output, errorMessage, data, true);
    }

    public ToolResult(ResultStatus status, String output, String errorMessage, Map<String, String> data, boolean retryable) {
        this.status = status;
        this.output = output;
        this.errorMessage = errorMessage;
        this.data = new HashMap<>(data);
        this.retryable = retryable;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, String> getData() {
        return new HashMap<>(data);
    }

    public boolean canRetry() {
        return status == ResultStatus.FAILED && retryable;
    }
}
