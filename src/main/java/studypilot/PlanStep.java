package studypilot;

import java.util.HashMap;
import java.util.Map;

public class PlanStep {
    private final String stepId;
    private final String toolName;
    private final Map<String, String> arguments;
    private int retriesUsed;

    public PlanStep(String stepId, String toolName, Map<String, String> arguments) {
        if (stepId == null || !stepId.trim().matches("[A-Za-z][A-Za-z0-9_]*")
                || toolName == null || toolName.isBlank() || arguments == null) {
            throw new IllegalArgumentException("A plan step needs an ID, a tool name and arguments.");
        }
        this.stepId = stepId.trim();
        this.toolName = toolName.trim();
        this.arguments = new HashMap<>(arguments);
    }

    public String getStepId() {
        return stepId;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, String> getArguments() {
        return new HashMap<>(arguments);
    }

    public int getRetriesUsed() {
        return retriesUsed;
    }

    public void recordRetry() {
        retriesUsed++;
    }
}
