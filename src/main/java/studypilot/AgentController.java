package studypilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public class AgentController {
    private static final int MAX_STEPS = 8;
    private static final int MAX_RETRIES_PER_STEP = 2;
    private static final int MAX_REPLANS = 2;
    private final AgentPlanner planner;
    private final ToolManager toolManager;
    private final MemoryManager memoryManager;
    private final List<String> trace = new ArrayList<>();
    private final List<PlanStep> steps = new ArrayList<>();
    private final Map<String, ToolResult> results = new HashMap<>();
    private final Map<PlanStep, ToolResult> attemptedActions = new HashMap<>();
    private final Map<PlanStep, Integer> actionPlans = new HashMap<>();
    private int replansUsed;
    private String problem;

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
        clearRequest();
        List<Message> history = memoryManager.getHistory();
        trace.add("Loaded session " + memoryManager.getSessionId() + " with " + history.size() + " earlier messages.");
        try {
            ExecutionPlan plan = planner.createPlan(courseId, request, history);
            while (true) {
                Decision decision = executePlan(courseId, plan);
                if (decision == Decision.CONTINUE) {
                    return finish(request, ResultStatus.OK);
                } else if (decision == Decision.CLARIFY) {
                    return finish(request, ResultStatus.INSUFFICIENT_DATA);
                } else if (decision == Decision.ABORT) {
                    return finish(request, ResultStatus.FAILED);
                }
                replansUsed++;
                trace.add("REPLAN " + replansUsed + " of " + MAX_REPLANS + ": " + problem);
                plan = planner.replan(courseId, request, history, describeResults());
            }
        } catch (IllegalStateException e) {
            problem += "\n" + e.getMessage();
            trace.add("ABORT: planning failed. " + problem);
            return finish(request, ResultStatus.FAILED);
        }
    }

    private void clearRequest() {
        trace.clear();
        steps.clear();
        results.clear();
        attemptedActions.clear();
        actionPlans.clear();
        replansUsed = 0;
        problem = "";
    }

    private Decision executePlan(String courseId, ExecutionPlan plan) {
        trace.add("Planner returned " + plan.size() + " steps.");
        if (plan.size() == 0) {
            problem = plan.getClarification();
            trace.add("CLARIFY: " + problem);
            return Decision.CLARIFY;
        }
        String error = validatePlan(plan);
        if (!error.isEmpty()) {
            return invalidStep(error);
        }
        for (PlanStep proposed : plan.getSteps()) {
            PlanStep step;
            try {
                step = toolManager.resolveStep(proposed, results);
            } catch (IllegalArgumentException e) {
                return invalidStep(e.getMessage());
            }
            if (!step.getArguments().equals(proposed.getArguments())) {
                trace.add("Resolved " + step.getStepId() + ": " + new JSONObject(proposed.getArguments())
                        + " -> " + new JSONObject(step.getArguments()));
            }
            PlanStep earlier = matchingAction(step);
            ToolResult result;
            if (earlier == null) {
                result = executeWithRetries(courseId, step);
                attemptedActions.put(step, result);
                actionPlans.put(step, replansUsed);
            } else {
                result = attemptedActions.get(earlier);
                trace.add("Reused " + earlier.getStepId() + " for " + step.getStepId() + "; no tool call repeated.");
                if (step.getStepId().equals(earlier.getStepId())) {
                    step = earlier;
                }
            }
            rememberResult(step, result);
            int retries = earlier == null ? step.getRetriesUsed() : earlier.getRetriesUsed();
            Decision decision = decide(result, retries, replansUsed);
            trace.add(step.getStepId() + " returned " + result.getStatus() + "; decision " + decision);
            if (decision != Decision.CONTINUE) {
                problem = result.getErrorMessage();
                return decision;
            }
        }
        problem = "";
        return Decision.CONTINUE;
    }

    private String validatePlan(ExecutionPlan plan) {
        if (plan.size() > MAX_STEPS) {
            return "A plan may contain at most 8 steps.";
        }
        String error = toolManager.validatePlan(plan, results);
        if (!error.isEmpty()) {
            return error;
        }
        for (PlanStep proposed : plan.getSteps()) {
            ToolResult previous = results.get(proposed.getStepId());
            if (previous != null && previous.getStatus() == ResultStatus.OK) {
                try {
                    PlanStep resolved = toolManager.resolveStep(proposed, results);
                    for (PlanStep saved : steps) {
                        if (saved.getStepId().equals(resolved.getStepId()) && !sameAction(saved, resolved)) {
                            return "A completed step ID cannot change its tool or arguments.";
                        }
                    }
                } catch (IllegalArgumentException e) {
                    return "A completed step ID cannot be redefined. " + e.getMessage();
                }
            }
        }
        return "";
    }

    private Decision invalidStep(String error) {
        problem = error;
        Decision decision = replansUsed < MAX_REPLANS ? Decision.REPLAN : Decision.ABORT;
        trace.add(decision + ": plan or arguments rejected before the invalid step ran. " + error);
        return decision;
    }

    private ToolResult executeWithRetries(String courseId, PlanStep step) {
        while (true) {
            trace.add("Calling " + step.getStepId() + ": " + step.getToolName() + " with "
                    + new JSONObject(step.getArguments()) + "; attempt " + (step.getRetriesUsed() + 1));
            ToolResult result = toolManager.execute(courseId, step);
            Decision decision = decide(result, step.getRetriesUsed(), replansUsed);
            if (decision != Decision.RETRY) {
                return result;
            }
            step.recordRetry();
            trace.add("RETRY " + step.getRetriesUsed() + " of " + MAX_RETRIES_PER_STEP
                    + " for " + step.getStepId() + ": " + result.getErrorMessage());
        }
    }

    private Decision decide(ToolResult result, int retriesUsed, int replansUsed) {
        if (result.getStatus() == ResultStatus.OK) {
            return Decision.CONTINUE;
        } else if (result.getStatus() == ResultStatus.INSUFFICIENT_DATA) {
            return Decision.CLARIFY;
        } else if (result.canRetry() && retriesUsed < MAX_RETRIES_PER_STEP) {
            return Decision.RETRY;
        } else if (replansUsed < MAX_REPLANS) {
            return Decision.REPLAN;
        }
        return Decision.ABORT;
    }

    private boolean sameAction(PlanStep first, PlanStep second) {
        return first.getToolName().equals(second.getToolName()) && first.getArguments().equals(second.getArguments());
    }

    private PlanStep matchingAction(PlanStep step) {
        // Replanning reuses prior actions; explicit repeats in one plan still run.
        PlanStep match = null;
        for (PlanStep earlier : attemptedActions.keySet()) {
            if (actionPlans.get(earlier) < replansUsed && sameAction(earlier, step)) {
                if (earlier.getStepId().equals(step.getStepId())) {
                    return earlier;
                }
                if (match == null || attemptedActions.get(earlier).getStatus() == ResultStatus.OK) {
                    match = earlier;
                }
            }
        }
        return match;
    }

    private void rememberResult(PlanStep step, ToolResult result) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStepId().equals(step.getStepId())) {
                steps.set(i, step);
                results.put(step.getStepId(), result);
                return;
            }
        }
        steps.add(step);
        results.put(step.getStepId(), result);
    }

    private String describeResults() {
        StringBuilder text = new StringBuilder("Problem: " + problem + "\n");
        for (PlanStep step : steps) {
            ToolResult result = results.get(step.getStepId());
            text.append(step.getStepId() + " " + step.getToolName() + " arguments=" + new JSONObject(step.getArguments())
                    + "; status=" + result.getStatus() + "; data=" + new JSONObject(result.getData()) + "\n");
            String output = result.getOutput();
            text.append(output.substring(0, Math.min(output.length(), 2000)) + "\n" + result.getErrorMessage() + "\n");
        }
        return text.toString();
    }

    private String finalAnswer(ResultStatus status) {
        StringBuilder answer = new StringBuilder();
        if (replansUsed > 0) {
            answer.append("Replans used: " + replansUsed + ". Earlier results are retained below.\n\n");
        }
        for (PlanStep step : steps) {
            ToolResult result = results.get(step.getStepId());
            answer.append(step.getStepId() + " - " + step.getToolName() + " [" + result.getStatus() + "]:\n");
            answer.append(result.getOutput() + "\n");
            if (!result.getErrorMessage().isBlank()) {
                answer.append(result.getErrorMessage() + "\n");
            }
            answer.append("\n");
        }
        if (status != ResultStatus.OK) {
            answer.append(problem + "\n");
            if (steps.isEmpty()) {
                answer.append("No tools were run.");
            } else {
                answer.append("The request stopped with the actual results above.");
            }
        }
        return answer.toString().trim();
    }

    private AgentResult finish(String request, ResultStatus status) {
        boolean needsClarification = status == ResultStatus.INSUFFICIENT_DATA;
        String answer = finalAnswer(status);
        try {
            memoryManager.addMessage(new Message("user", request));
            memoryManager.addMessage(new Message("assistant", answer));
        } catch (IllegalArgumentException | IllegalStateException e) {
            trace.add("Memory save failed: " + e.getMessage());
            answer += "\nConversation could not be saved: " + e.getMessage();
            status = ResultStatus.FAILED;
        }
        return new AgentResult(memoryManager.getSessionId(), status, answer, needsClarification, trace, steps, results);
    }
}
