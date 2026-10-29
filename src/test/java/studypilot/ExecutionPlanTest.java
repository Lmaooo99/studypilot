package studypilot;

import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExecutionPlanTest {
    @Test
    public void readsFencedPlanJsonAndStringArguments() {
        String text = "```json\n{\"steps\":[{\"stepId\":\"step1\",\"toolName\":\"quiz\","
                + "\"arguments\":{\"topic\":\"Observer\",\"count\":\"2\"}}]}\n```";
        ExecutionPlan plan = new ResponseParser().parseExecutionPlan(text);
        assertEquals(1, plan.size());
        assertEquals("step1", plan.getSteps().get(0).getStepId());
        assertEquals("2", plan.getSteps().get(0).getArguments().get("count"));
    }

    @Test
    public void readsAnExplicitClarificationWithoutTools() {
        ExecutionPlan plan = new ResponseParser().parseExecutionPlan("{\"steps\":[],\"clarification\":\"Which topic?\"}");
        assertEquals(0, plan.size());
        assertEquals("Which topic?", plan.getClarification());
    }

    @Test(expected = JSONException.class)
    public void rejectsNumericArgumentsInsteadOfCoercingThem() {
        new ResponseParser().parseExecutionPlan("{\"steps\":[{\"stepId\":\"step1\",\"toolName\":\"quiz\",\"arguments\":{\"count\":2}}]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAReplyThatContainsBothStepsAndAClarification() {
        new ResponseParser().parseExecutionPlan("{\"steps\":[{\"stepId\":\"step1\",\"toolName\":\"progress\",\"arguments\":{}}],\"clarification\":\"Which topic?\"}");
    }
}
