package studypilot;

import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolManager {
    private final Map<String, Tool> tools = new HashMap<>();

    public void registerTool(Tool tool) {
        if (tools.containsKey(tool.getName())) {
            throw new IllegalArgumentException("A tool with this name is already registered.");
        }
        tools.put(tool.getName(), tool);
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public String getToolDescriptions() {
        StringBuilder text = new StringBuilder();
        for (Tool tool : tools.values()) {
            text.append(tool.getName() + ": " + tool.getDescription());
            text.append(" Required: " + String.join(", ", tool.getRequiredArguments()));
            text.append(". Optional: " + String.join(", ", tool.getOptionalArguments()) + ".\n");
        }
        return text.toString();
    }

    public String validatePlan(ExecutionPlan plan) {
        List<String> ids = new ArrayList<>();
        for (PlanStep step : plan.getSteps()) {
            if (ids.contains(step.getStepId())) {
                return "Plan step IDs must be distinct.";
            }
            ids.add(step.getStepId());
            String error = validateArguments(step);
            if (!error.isEmpty()) {
                return error;
            }
        }
        return "";
    }

    private String validateArguments(PlanStep step) {
        Tool tool = tools.get(step.getToolName());
        if (tool == null) {
            return "Unknown tool: " + step.getToolName();
        }
        Map<String, String> arguments = step.getArguments();
        for (String name : tool.getRequiredArguments()) {
            if (!arguments.containsKey(name) || arguments.get(name) == null || arguments.get(name).isBlank()) {
                return "Missing argument for " + tool.getName() + ": " + name;
            }
        }
        for (String name : arguments.keySet()) {
            if (!contains(tool.getRequiredArguments(), name) && !contains(tool.getOptionalArguments(), name)) {
                return "Unexpected argument for " + tool.getName() + ": " + name;
            }
            String value = arguments.get(name);
            if (value == null || value.isBlank()) {
                return "Tool arguments must be nonempty strings.";
            }
            if (value.matches("\\$[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_]*")) {
                return "This agent version requires literal arguments; step references are not available yet.";
            }
        }
        return "";
    }

    private boolean contains(String[] names, String target) {
        for (String name : names) {
            if (name.equals(target)) {
                return true;
            }
        }
        return false;
    }

    public ToolResult execute(String courseId, PlanStep step) {
        String error = validateArguments(step);
        if (!error.isEmpty()) {
            return new ToolResult(ResultStatus.FAILED, "", error);
        }
        Map<String, String> arguments = step.getArguments();
        arguments.put("courseId", courseId);
        try {
            return tools.get(step.getToolName()).execute(arguments);
        } catch (IllegalArgumentException | IllegalStateException | DateTimeException e) {
            return new ToolResult(ResultStatus.FAILED, "", e.getMessage());
        }
    }
}
