package studypilot;

import java.util.ArrayList;
import java.util.List;

public class ExecutionPlan {
    private final List<PlanStep> steps;
    private final String clarification;

    public ExecutionPlan(List<PlanStep> steps, String clarification) {
        if (steps == null || clarification == null || (steps.isEmpty() && clarification.isBlank())
                || (!steps.isEmpty() && !clarification.isBlank())) {
            throw new IllegalArgumentException("Return tool steps or a clarification question, not both.");
        }
        this.steps = new ArrayList<>(steps);
        this.clarification = clarification.trim();
    }

    public List<PlanStep> getSteps() {
        return new ArrayList<>(steps);
    }

    public int size() {
        return steps.size();
    }

    public String getClarification() {
        return clarification;
    }
}
