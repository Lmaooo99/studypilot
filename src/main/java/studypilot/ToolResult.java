package studypilot;

import java.util.HashMap;
import java.util.Map;

public class ToolResult {
    private final ResultStatus status;
    private final String output;
    private final String errorMessage;
    private final Map<String, String> data;

    public ToolResult(ResultStatus status, String output, String errorMessage) {
        this(status, output, errorMessage, new HashMap<String, String>());
    }

    public ToolResult(ResultStatus status, String output, String errorMessage, Map<String, String> data) {
        this.status = status;
        this.output = output;
        this.errorMessage = errorMessage;
        this.data = new HashMap<>(data);
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
}
