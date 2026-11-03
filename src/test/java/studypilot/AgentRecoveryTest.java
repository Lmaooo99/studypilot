package studypilot;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgentRecoveryTest {
    private MockLLMClient llm;
    private AgentController controller;
    private Course course;
    private MemoryRepository memory;
    private ToolManager tools;
    private TestTool work;
    private TestTool followup;

    @Before
    public void setUp() throws Exception {
        AppConfig config = new AppConfig(Files.createTempDirectory("studypilot-recovery-test-"));
        CourseService courses = new CourseService(new CourseRepository(config.getDataDirectory()));
        course = courses.createCourse("Design");
        llm = new MockLLMClient();
        tools = new ToolManager();
        work = new TestTool("work");
        followup = new TestTool("followup");
        tools.registerTool(work);
        tools.registerTool(followup);
        memory = new MemoryRepository(config.getDataDirectory());
        AgentPlanner planner = new AgentPlanner(llm, tools, new PromptTemplates(), new ResponseParser(), courses);
        controller = new AgentController(planner, tools, new MemoryManager(memory));
    }

    private JSONObject step(String id, String tool, String value) {
        return new JSONObject().put("stepId", id).put("toolName", tool)
                .put("arguments", new JSONObject().put("value", value));
    }

    private void plan(JSONObject... steps) {
        JSONArray items = new JSONArray();
        for (JSONObject step : steps) {
            items.put(step);
        }
        llm.reply(new JSONObject().put("steps", items).toString());
    }

    private AgentResult run() {
        return controller.handleRequest(course.getId(), null, "Complete the study request.");
    }

    @Test
    public void retriesATransientFailureOnceAndRecordsTheActualRetry() {
        work.failures = 1;
        plan(step("first", "work", "Observer"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(2, work.calls);
        assertEquals(1, llm.getPrompts().size());
        assertEquals(1, new JSONObject(result.toJson()).getJSONArray("plan").getJSONObject(0).getInt("retriesUsed"));
        assertTrue(result.getTrace().toString().contains("RETRY 1 of 2"));
        assertEquals(2, memory.loadConversation(course.getId(), result.getSessionId()).size());
    }

    @Test
    public void permitsTwoExtraRetriesButNoFourthCall() {
        work.failures = 2;
        plan(step("first", "work", "Observer"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(3, work.calls);
        assertEquals(2, new JSONObject(result.toJson()).getJSONArray("plan").getJSONObject(0).getInt("retriesUsed"));
    }

    @Test
    public void stopsAfterTwoReplansAndDoesNotResetTheFailedActionBudget() {
        work.failures = 100;
        plan(step("first", "work", "Observer"));
        plan(step("renamed", "work", "Observer"));
        plan(step("renamedAgain", "work", "Observer"));
        AgentResult result = run();
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertEquals(3, work.calls);
        assertEquals(3, llm.getPrompts().size());
        assertTrue(result.getTrace().toString().contains("REPLAN 2 of 2"));
        assertTrue(result.getTrace().toString().contains("ABORT"));
    }

    @Test
    public void canRepairFailedArgumentsWithoutRepeatingAnEarlierSuccess() {
        followup.failures = 3;
        plan(step("first", "work", "Observer"), step("second", "followup", "old"));
        plan(step("first", "work", "Observer"), step("second", "followup", "$first.topic"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(1, work.calls);
        assertEquals(4, followup.calls);
        assertEquals("Observer", followup.lastValue);
        assertTrue(llm.getPrompts().get(1).contains("status=OK"));
        assertTrue(llm.getPrompts().get(1).contains("Test failure"));
        assertTrue(result.getTrace().toString().contains("Reused first"));
    }

    @Test
    public void reusesASuccessEvenWhenTheReplannerRenamesIt() {
        followup.failures = 3;
        plan(step("first", "work", "Observer"), step("second", "followup", "old"));
        plan(step("saved", "work", "Observer"), step("third", "followup", "$saved.topic"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(1, work.calls);
        assertEquals("Observer", result.getResults().get("saved").getData().get("topic"));
        assertEquals("Observer", followup.lastValue);
        assertTrue(result.getFinalAnswer().contains("[FAILED]"));
    }

    @Test
    public void rejectsAChangedCompletedIdBeforeAnyNewToolRuns() {
        followup.failures = 100;
        plan(step("first", "work", "Observer"), step("second", "followup", "old"));
        plan(step("third", "work", "new"), step("first", "work", "changed"));
        llm.reply("{\"steps\":[],\"clarification\":\"Which remaining activity should I use?\"}");
        AgentResult result = run();
        assertEquals(ResultStatus.INSUFFICIENT_DATA, result.getStatus());
        assertEquals(1, work.calls);
        assertEquals(3, followup.calls);
        assertTrue(result.getTrace().toString().contains("completed step ID cannot change"));
        assertFalse(result.getResults().containsKey("third"));
    }

    @Test
    public void resolvesADependencyFromTheRealEarlierResult() {
        plan(step("first", "work", "Observer"), step("second", "followup", "$first.topic"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals("Observer", followup.lastValue);
        assertEquals(course.getId(), followup.lastCourseId);
        assertEquals(1, work.calls);
        assertEquals(1, followup.calls);
        assertTrue(result.getTrace().toString().contains("Resolved second"));
        assertTrue(result.getTrace().toString().contains("$first.topic"));
    }

    @Test
    public void replansAMissingResultFieldWithoutRunningItsDependentTool() {
        plan(step("first", "work", "Observer"), step("second", "followup", "$first.missing"));
        plan(step("second", "followup", "$first.topic"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(1, work.calls);
        assertEquals(1, followup.calls);
        assertEquals("Observer", followup.lastValue);
        assertTrue(result.getTrace().toString().contains("No successful result field"));
    }

    @Test
    public void replansAnUnknownToolBeforeExecutingAnyOfThatPlan() {
        plan(step("first", "work", "Observer"), step("second", "unknown", "value"));
        plan(step("first", "work", "Observer"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(1, work.calls);
        assertEquals(2, llm.getPrompts().size());
        assertTrue(result.getTrace().toString().contains("Unknown tool"));
    }

    @Test
    public void rejectsForwardDependenciesBeforeAnyToolRuns() {
        plan(step("first", "work", "$second.topic"), step("second", "followup", "Observer"));
        llm.reply("{\"steps\":[],\"clarification\":\"Which topic should I use?\"}");
        AgentResult result = run();
        assertEquals(ResultStatus.INSUFFICIENT_DATA, result.getStatus());
        assertEquals(0, work.calls);
        assertEquals(0, followup.calls);
    }

    @Test
    public void clarifiesInsufficientDataWithoutRetryingOrReplanning() {
        work.insufficient = true;
        plan(step("first", "work", "Observer"), step("second", "followup", "$first.topic"));
        AgentResult result = run();
        assertEquals(ResultStatus.INSUFFICIENT_DATA, result.getStatus());
        assertTrue(result.needsClarification());
        assertEquals(1, work.calls);
        assertEquals(0, followup.calls);
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void keepsAnExplicitRepeatedActionWithinTheOriginalPlan() {
        plan(step("first", "work", "Observer"), step("second", "work", "Observer"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(2, work.calls);
    }

    @Test
    public void doesNotReuseToolResultsAcrossSeparateRequests() {
        plan(step("first", "work", "Observer"));
        AgentResult first = run();
        plan(step("first", "work", "Observer"));
        AgentResult second = controller.handleRequest(course.getId(), first.getSessionId(), "Do another activity.");
        assertEquals(ResultStatus.OK, second.getStatus());
        assertEquals(2, work.calls);
        assertEquals(4, memory.loadConversation(course.getId(), first.getSessionId()).size());
    }

    @Test
    public void replansInvalidInputWithoutRetryingThatToolCall() {
        work.invalidInput = true;
        plan(step("first", "work", "Observer"));
        plan(step("second", "followup", "Observer"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(1, work.calls);
        assertEquals(1, followup.calls);
        assertFalse(result.getResults().get("first").canRetry());
        assertFalse(result.getTrace().toString().contains("RETRY"));
    }

    @Test
    public void rejectsASelfReferenceBeforeAnyToolRuns() {
        plan(step("first", "work", "$first.topic"));
        llm.reply("{\"steps\":[],\"clarification\":\"Which topic?\"}");
        AgentResult result = run();
        assertEquals(ResultStatus.INSUFFICIENT_DATA, result.getStatus());
        assertEquals(0, work.calls);
    }

    @Test
    public void rejectsEmbeddedReferencesInsteadOfGuessingReplacementText() {
        plan(step("first", "work", "Observer"), step("second", "followup", "Explain $first.topic"));
        llm.reply("{\"steps\":[],\"clarification\":\"Which topic?\"}");
        AgentResult result = run();
        assertEquals(ResultStatus.INSUFFICIENT_DATA, result.getStatus());
        assertTrue(result.getFinalAnswer().contains("Which topic"));
        assertEquals(0, work.calls);
        assertEquals(0, followup.calls);
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesDataFromAFailedResult() {
        Map<String, String> data = new HashMap<>();
        data.put("topic", "Untrusted result");
        Map<String, ToolResult> previous = new HashMap<>();
        previous.put("first", new ToolResult(ResultStatus.FAILED, "", "Test failure.", data));
        Map<String, String> arguments = new HashMap<>();
        arguments.put("value", "$first.topic");
        tools.resolveStep(new PlanStep("second", "followup", arguments), previous);
    }

    @Test
    public void acceptsExactlyEightStepsAndRunsEachExplicitStep() {
        JSONObject[] steps = new JSONObject[8];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = step("step" + i, "work", "Observer");
        }
        plan(steps);
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(8, work.calls);
        assertEquals(8, result.getResults().size());
    }

    @Test
    public void retainsTheCompletedIdWhenAnIdenticalLaterActionFailed() {
        work.failAfterFirst = true;
        plan(step("first", "work", "Observer"), step("second", "work", "Observer"));
        plan(step("first", "work", "Observer"), step("third", "followup", "$first.topic"));
        AgentResult result = run();
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(4, work.calls);
        assertEquals(ResultStatus.OK, result.getResults().get("first").getStatus());
        assertEquals(ResultStatus.FAILED, result.getResults().get("second").getStatus());
        assertEquals("Observer", followup.lastValue);
    }

    // Test double: controlled failures let the tests count actual dispatches.
    private static class TestTool implements Tool {
        private final String name;
        private int failures;
        private int calls;
        private boolean insufficient;
        private boolean invalidInput;
        private boolean failAfterFirst;
        private String lastValue;
        private String lastCourseId;

        TestTool(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        public String getDescription() {
            return "Test tool with controlled results.";
        }
        public String[] getRequiredArguments() {
            return new String[] {"value"};
        }
        public String[] getOptionalArguments() {
            return new String[0];
        }
        public ToolResult execute(Map<String, String> arguments) {
            calls++;
            lastValue = arguments.get("value");
            lastCourseId = arguments.get("courseId");
            if (invalidInput) {
                throw new IllegalArgumentException("Test invalid input.");
            }
            if (insufficient) {
                return new ToolResult(ResultStatus.INSUFFICIENT_DATA, "", "Which topic should I use?");
            }
            if (calls <= failures || (failAfterFirst && calls > 1)) {
                return new ToolResult(ResultStatus.FAILED, "", "Test failure.");
            }
            Map<String, String> data = new HashMap<>();
            data.put("topic", lastValue);
            return new ToolResult(ResultStatus.OK, "Completed " + lastValue, "", data);
        }
    }
}
