package studypilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class AgentResult {
    private final String finalAnswer;
    private final boolean needsClarification;
    private final List<String> trace;
    private final String sessionId;
    private final ResultStatus status;
    private final List<PlanStep> plan;
    private final Map<String, ToolResult> results;

    public AgentResult(String sessionId, ResultStatus status, String finalAnswer, boolean needsClarification, List<String> trace,
                       List<PlanStep> plan, Map<String, ToolResult> results) {
        this.sessionId = sessionId;
        this.status = status;
        this.finalAnswer = finalAnswer;
        this.needsClarification = needsClarification;
        this.trace = new ArrayList<>(trace);
        this.plan = new ArrayList<>(plan);
        this.results = new HashMap<>(results);
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public boolean needsClarification() {
        return needsClarification;
    }

    public List<String> getTrace() {
        return new ArrayList<>(trace);
    }

    public String getSessionId() {
        return sessionId;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public Map<String, ToolResult> getResults() {
        return new HashMap<>(results);
    }

    public String toJson() {
        JSONObject data = new JSONObject();
        data.put("sessionId", sessionId);
        data.put("status", status.name());
        data.put("finalAnswer", finalAnswer);
        data.put("needsClarification", needsClarification);
        data.put("trace", new JSONArray(trace));
        JSONArray steps = new JSONArray();
        JSONArray outcomes = new JSONArray();
        for (PlanStep step : plan) {
            JSONObject item = new JSONObject();
            item.put("stepId", step.getStepId());
            item.put("toolName", step.getToolName());
            item.put("arguments", new JSONObject(step.getArguments()));
            steps.put(item);
            ToolResult result = results.get(step.getStepId());
            if (result != null) {
                JSONObject outcome = new JSONObject();
                outcome.put("stepId", step.getStepId());
                outcome.put("toolName", step.getToolName());
                outcome.put("status", result.getStatus().name());
                outcome.put("output", result.getOutput());
                outcome.put("errorMessage", result.getErrorMessage());
                outcome.put("data", new JSONObject(result.getData()));
                outcomes.put(outcome);
            }
        }
        data.put("plan", steps);
        data.put("results", outcomes);
        return data.toString();
    }
}
