package studypilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentController {
    private static final int MAX_STEPS = 8;
    private final AgentPlanner planner;
    private final ToolManager toolManager;
    private final MemoryManager memoryManager;
    private final List<String> trace = new ArrayList<>();

    public AgentController(AgentPlanner planner, ToolManager toolManager, MemoryManager memoryManager) {
        this.planner = planner;
        this.toolManager = toolManager;
        this.memoryManager = memoryManager;
    }

    public AgentResult handleRequest(String courseId, String sessionId, String request) {
        if (request == null || request.isBlank()) {
            throw new IllegalArgumentException("Enter a study request.");
        }
        memoryManager.loadSession(courseId, sessionId);
        trace.clear();
        List<Message> history = memoryManager.getHistory();
        trace.add("Loaded session " + memoryManager.getSessionId() + " with " + history.size() + " earlier messages.");
        Map<String, ToolResult> results = new HashMap<>();
        ExecutionPlan plan;
        try {
            plan = planner.createPlan(courseId, request, history);
        } catch (IllegalStateException e) {
            trace.add("ABORT: planning failed.");
            return finish(request, ResultStatus.FAILED, e.getMessage(), new ArrayList<PlanStep>(), results);
        }
        trace.add("Planner returned " + plan.size() + " steps.");
        if (plan.size() == 0) {
            trace.add("CLARIFY: more information is needed.");
            return finish(request, ResultStatus.INSUFFICIENT_DATA, plan.getClarification(), plan.getSteps(), results);
        }
        String error = "";
        if (plan.size() > MAX_STEPS) {
            error = "A plan may contain at most 8 steps.";
        } else {
            error = toolManager.validatePlan(plan);
        }
        if (!error.isEmpty()) {
            trace.add("ABORT: plan rejected before execution.");
            return finish(request, ResultStatus.FAILED, error, plan.getSteps(), results);
        }
        StringBuilder answer = new StringBuilder();
        ResultStatus status = ResultStatus.OK;
        for (PlanStep step : plan.getSteps()) {
            trace.add("Calling " + step.getStepId() + ": " + step.getToolName());
            ToolResult result = toolManager.execute(courseId, step);
            results.put(step.getStepId(), result);
            appendResult(answer, step, result);
            Decision decision = decide(result);
            trace.add(step.getStepId() + " returned " + result.getStatus() + "; decision " + decision);
            if (decision != Decision.CONTINUE) {
                status = result.getStatus();
                break;
            }
        }
        trace.add("Processed " + results.size() + " of " + plan.size() + " planned steps.");
        if (results.size() < plan.size()) {
            answer.append("\nProcessed " + results.size() + " of " + plan.size() + " planned steps.");
        }
        return finish(request, status, answer.toString().trim(), plan.getSteps(), results);
    }

    private Decision decide(ToolResult result) {
        if (result.getStatus() == ResultStatus.OK) {
            return Decision.CONTINUE;
        } else if (result.getStatus() == ResultStatus.INSUFFICIENT_DATA) {
            return Decision.CLARIFY;
        }
        return Decision.ABORT;
    }

    private void appendResult(StringBuilder answer, PlanStep step, ToolResult result) {
        answer.append(step.getStepId() + " - " + step.getToolName() + ":\n");
        if (!result.getOutput().isBlank()) {
            answer.append(result.getOutput() + "\n");
        }
        if (!result.getErrorMessage().isBlank() && !result.getErrorMessage().equals(result.getOutput())) {
            answer.append(result.getErrorMessage() + "\n");
        }
        answer.append("\n");
    }

    private AgentResult finish(String request, ResultStatus status, String answer,
                               List<PlanStep> plan, Map<String, ToolResult> results) {
        boolean needsClarification = status == ResultStatus.INSUFFICIENT_DATA;
        try {
            memoryManager.addMessage(new Message("user", request));
            memoryManager.addMessage(new Message("assistant", answer));
        } catch (IllegalArgumentException | IllegalStateException e) {
            trace.add("Memory save failed: " + e.getMessage());
            answer += "\nConversation could not be saved: " + e.getMessage();
            status = ResultStatus.FAILED;
        }
        return new AgentResult(memoryManager.getSessionId(), status, answer, needsClarification, trace, plan, results);
    }
}
