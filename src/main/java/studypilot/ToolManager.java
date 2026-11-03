package studypilot;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        return validatePlan(plan, new HashMap<String, ToolResult>());
    }

    public String validatePlan(ExecutionPlan plan, Map<String, ToolResult> results) {
        List<String> ids = new ArrayList<>();
        List<String> available = new ArrayList<>();
        for (String id : results.keySet()) {
            if (results.get(id).getStatus() == ResultStatus.OK) {
                available.add(id);
            }
        }
        for (PlanStep step : plan.getSteps()) {
            if (ids.contains(step.getStepId())) {
                return "Plan step IDs must be distinct.";
            }
            ids.add(step.getStepId());
            String error = validateArguments(step, true);
            if (!error.isEmpty()) {
                return error;
            }
            for (String value : step.getArguments().values()) {
                if (isReference(value) && !available.contains(referenceId(value))) {
                    return "A reference must name an earlier step or a completed result: " + value;
                }
            }
            available.add(step.getStepId());
        }
        return "";
    }

    private String validateArguments(PlanStep step, boolean allowReferences) {
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
            if (isReference(value)) {
                if (!allowReferences) {
                    return "Resolve step references before calling a tool.";
                }
            } else if (value.matches(".*\\$[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_]*.*")) {
                return "A step reference must fill the whole argument value.";
            }
        }
        return validateValues(step);
    }

    private String validateValues(PlanStep step) {
        Map<String, String> values = step.getArguments();
        try {
            if (step.getToolName().equals("quiz")) {
                validateQuiz(values);
            } else if (step.getToolName().equals("studyPlan")) {
                validateStudyPlan(values);
            } else if (step.getToolName().equals("summarize")) {
                validateSummary(values);
            }
        } catch (IllegalArgumentException | DateTimeException e) {
            return "Invalid argument values for " + step.getToolName() + ": " + e.getMessage();
        }
        return "";
    }

    private void validateQuiz(Map<String, String> values) {
        String countValue = values.getOrDefault("count", "6");
        int count = -1;
        if (!isReference(countValue)) {
            count = Integer.parseInt(countValue);
            if (count < 1 || count > 50) {
                throw new IllegalArgumentException("Question count must be from 1 to 50.");
            }
        }
        List<QuestionType> types = new ArrayList<>();
        String typeValue = values.getOrDefault("types", "multiple-choice,true-false,short-answer");
        if (!isReference(typeValue)) {
            for (String name : typeValue.split(",", -1)) {
                QuestionType type = QuestionType.parse(name);
                if (types.contains(type)) {
                    throw new IllegalArgumentException("Question types must be distinct.");
                }
                types.add(type);
            }
        }
        if (count >= 1 && count < types.size()) {
            throw new IllegalArgumentException("Question count must cover the selected types.");
        }
        if (values.containsKey("difficulty") && !isReference(values.get("difficulty"))) {
            Difficulty.parse(values.get("difficulty"));
        }
    }

    private void validateStudyPlan(Map<String, String> values) {
        String date = values.get("examDate");
        if (!isReference(date) && LocalDate.parse(date).isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("The exam date cannot be in the past.");
        }
        String hours = values.get("hoursPerDay");
        if (!isReference(hours) && (Integer.parseInt(hours) < 1 || Integer.parseInt(hours) > 24)) {
            throw new IllegalArgumentException("Hours per day must be a whole number from 1 to 24.");
        }
    }

    private void validateSummary(Map<String, String> values) {
        String id = values.get("materialId");
        if (!isReference(id) && !UUID.fromString(id).toString().equals(id)) {
            throw new IllegalArgumentException("The material ID must be a UUID.");
        }
        String style = values.getOrDefault("style", "brief");
        if (!isReference(style) && !style.equals("brief") && !style.equals("detailed") && !style.equals("bullet")) {
            throw new IllegalArgumentException("Summary style must be brief, detailed or bullet.");
        }
    }

    private boolean contains(String[] names, String target) {
        for (String name : names) {
            if (name.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReference(String value) {
        return value.matches("\\$[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_]*");
    }

    private String referenceId(String value) {
        return value.substring(1, value.indexOf('.'));
    }

    public PlanStep resolveStep(PlanStep step, Map<String, ToolResult> results) {
        String initialError = validateArguments(step, true);
        if (!initialError.isEmpty()) {
            throw new IllegalArgumentException(initialError);
        }
        Map<String, String> arguments = step.getArguments();
        for (String name : arguments.keySet()) {
            String value = arguments.get(name);
            if (isReference(value)) {
                ToolResult result = results.get(referenceId(value));
                String field = value.substring(value.indexOf('.') + 1);
                if (result == null || result.getStatus() != ResultStatus.OK || !result.getData().containsKey(field)) {
                    throw new IllegalArgumentException("No successful result field for " + value);
                }
                arguments.put(name, result.getData().get(field));
            }
        }
        PlanStep resolved = new PlanStep(step.getStepId(), step.getToolName(), arguments);
        String error = validateArguments(resolved, false);
        if (!error.isEmpty()) {
            throw new IllegalArgumentException(error);
        }
        return resolved;
    }

    public ToolResult execute(String courseId, PlanStep step) {
        String error = validateArguments(step, false);
        if (!error.isEmpty()) {
            return new ToolResult(ResultStatus.FAILED, "", error, new HashMap<String, String>(), false);
        }
        Map<String, String> arguments = step.getArguments();
        arguments.put("courseId", courseId);
        try {
            return tools.get(step.getToolName()).execute(arguments);
        } catch (IllegalArgumentException | DateTimeException e) {
            return new ToolResult(ResultStatus.FAILED, "", e.getMessage(), new HashMap<String, String>(), false);
        } catch (IllegalStateException e) {
            return new ToolResult(ResultStatus.FAILED, "", e.getMessage());
        }
    }
}
