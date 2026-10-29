package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgentControllerTest {
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private Material material;

    @Before
    public void setUp() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-agent-test-");
        config = new AppConfig(directory);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        Path file = directory.resolve("notes.md");
        Files.writeString(file, "Observer lets a subject notify its subscribers.");
        material = facade.importMaterial(course.getId(), file.toString());
    }

    private JSONObject step(String id, String tool, JSONObject arguments) {
        return new JSONObject().put("stepId", id).put("toolName", tool).put("arguments", arguments);
    }

    private String plan(JSONObject... steps) {
        JSONArray items = new JSONArray();
        for (JSONObject step : steps) {
            items.put(step);
        }
        return new JSONObject().put("steps", items).put("clarification", "").toString();
    }

    @Test
    public void executesTwoRealToolsAndSavesBothConversationMessages() {
        llm.reply(plan(step("step1", "progress", new JSONObject()),
                step("step2", "summarize", new JSONObject().put("materialId", material.getId()))));
        llm.reply("A subject notifies its subscribers.");
        AgentResult result = facade.runAgent(course.getId(), null, "Show progress and summarize notes.md.");
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(2, result.getResults().size());
        assertTrue(result.getFinalAnswer().contains("Reviews completed: 0"));
        assertTrue(result.getFinalAnswer().contains("A subject notifies"));
        assertEquals("A subject notifies its subscribers.", facade.getCourse(course.getId()).getMaterial(material.getId()).getSummary());
        List<Message> messages = new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), result.getSessionId());
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals(result.getFinalAnswer(), messages.get(1).getText());
        assertEquals(2, new JSONObject(result.toJson()).getJSONArray("results").length());
    }

    @Test
    public void restoresTheEarlierMessagesForAFollowupAfterRestart() {
        llm.reply(plan(step("step1", "progress", new JSONObject())));
        AgentResult first = facade.runAgent(course.getId(), null, "Show progress for my Observer revision.");
        MockLLMClient next = new MockLLMClient();
        next.reply(plan(step("step1", "progress", new JSONObject())));
        AgentFacade restarted = new AgentFacade(config, next);
        AgentResult second = restarted.runAgent(course.getId(), first.getSessionId(), "Show that again.");
        assertEquals(first.getSessionId(), second.getSessionId());
        assertTrue(next.getPrompts().get(0).contains("Observer revision"));
        assertTrue(next.getPrompts().get(0).contains(first.getFinalAnswer()));
        assertEquals(4, new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), first.getSessionId()).size());
    }

    @Test
    public void rejectsAnUnknownToolBeforeExecutingAnyStep() {
        llm.reply(plan(step("step1", "progress", new JSONObject()), step("step2", "unknown", new JSONObject())));
        AgentResult result = facade.runAgent(course.getId(), null, "Try the request.");
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertTrue(result.getResults().isEmpty());
        assertTrue(result.getFinalAnswer().contains("Unknown tool"));
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void rejectsRepeatedStepIdsBeforeExecution() {
        llm.reply(plan(step("step1", "progress", new JSONObject()), step("step1", "progress", new JSONObject())));
        AgentResult result = facade.runAgent(course.getId(), null, "Show progress.");
        assertTrue(result.getResults().isEmpty());
        assertTrue(result.getFinalAnswer().contains("distinct"));
    }

    @Test
    public void rejectsPlansLongerThanEightSteps() {
        JSONArray steps = new JSONArray();
        for (int i = 1; i <= 9; i++) {
            steps.put(step("step" + i, "progress", new JSONObject()));
        }
        llm.reply(new JSONObject().put("steps", steps).toString());
        AgentResult result = facade.runAgent(course.getId(), null, "Show progress.");
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertTrue(result.getResults().isEmpty());
        assertTrue(result.getFinalAnswer().contains("at most 8"));
    }

    @Test
    public void rejectsMissingRequiredArgumentsBeforeAServiceCall() {
        llm.reply(plan(step("step1", "summarize", new JSONObject())));
        AgentResult result = facade.runAgent(course.getId(), null, "Summarize notes.");
        assertTrue(result.getFinalAnswer().contains("Missing argument"));
        assertTrue(result.getResults().isEmpty());
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void rejectsAModelSuppliedCourseOverride() {
        Course other = facade.createCourse("Other");
        llm.reply(plan(step("step1", "progress", new JSONObject().put("courseId", other.getId()))));
        AgentResult result = facade.runAgent(course.getId(), null, "Show progress.");
        assertTrue(result.getFinalAnswer().contains("Unexpected argument"));
        assertTrue(result.getResults().isEmpty());
        assertTrue(facade.getHistory(other.getId()).isEmpty());
    }

    @Test
    public void rejectsStepReferencesUntilReferenceResolutionIsImplemented() {
        llm.reply(plan(step("step1", "quiz", new JSONObject().put("topic", "$earlier.topic"))));
        AgentResult result = facade.runAgent(course.getId(), null, "Create a quiz.");
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertTrue(result.getFinalAnswer().contains("literal arguments"));
        assertTrue(result.getResults().isEmpty());
        assertTrue(facade.listQuizzes(course.getId()).isEmpty());
    }

    @Test
    public void stopsOnFailureAndRetainsTheEarlierActualResult() {
        llm.reply(plan(step("step1", "progress", new JSONObject()),
                step("step2", "summarize", new JSONObject().put("materialId", material.getId())),
                step("step3", "progress", new JSONObject())));
        llm.failNext();
        llm.failNext();
        AgentResult result = facade.runAgent(course.getId(), null, "Show progress and summarize notes.");
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertEquals(2, result.getResults().size());
        assertEquals(ResultStatus.OK, result.getResults().get("step1").getStatus());
        assertFalse(result.getResults().containsKey("step3"));
        assertTrue(result.getFinalAnswer().contains("Processed 2 of 3"));
        assertEquals(3, llm.getPrompts().size());
    }

    @Test
    public void clarifiesInsufficientHistoryWithoutGeneratingAFakeWeakTopicQuiz() {
        llm.reply(plan(step("step1", "weakTopics", new JSONObject()),
                step("step2", "quiz", new JSONObject().put("topic", "Observer"))));
        AgentResult result = facade.runAgent(course.getId(), null, "Find a weak topic and quiz me.");
        assertEquals(ResultStatus.INSUFFICIENT_DATA, result.getStatus());
        assertTrue(result.needsClarification());
        assertTrue(result.getFinalAnswer().contains("no weakest topic is identified"));
        assertTrue(result.getFinalAnswer().contains("Which topic"));
        assertEquals(1, result.getResults().size());
        assertTrue(facade.listQuizzes(course.getId()).isEmpty());
    }

    @Test
    public void retriesAMalformedPlanningReplyOnce() {
        llm.reply("not JSON");
        llm.reply(plan(step("step1", "progress", new JSONObject())));
        AgentResult result = facade.runAgent(course.getId(), null, "Show progress.");
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(2, llm.getPrompts().size());
    }

    @Test
    public void recordsAnHonestFailureAfterBothPlanningAttemptsFail() {
        llm.failNext();
        llm.reply("invalid JSON");
        AgentResult result = facade.runAgent(course.getId(), null, "Show progress.");
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertTrue(result.getResults().isEmpty());
        assertEquals(2, llm.getPrompts().size());
        assertEquals(2, new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), result.getSessionId()).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsABlankRequestBeforePlanning() {
        facade.runAgent(course.getId(), null, " ");
    }

    @Test
    public void retainsToolResultsWhenSavingConversationFails() {
        MemoryRepository failing = new MemoryRepository(config.getDataDirectory()) {
            public void saveConversation(String courseId, String sessionId, List<Message> messages) {
                throw new IllegalStateException("Test memory save failure.");
            }
        };
        CourseService courses = new CourseService(new CourseRepository(config.getDataDirectory()));
        ToolManager tools = new ToolManager();
        tools.registerTool(new ProgressTool(courses, new ProgressTracker(new ProgressRepository(config.getDataDirectory()))));
        AgentPlanner planner = new AgentPlanner(llm, tools, new PromptTemplates(), new ResponseParser(), courses);
        AgentController controller = new AgentController(planner, tools, new MemoryManager(failing));
        llm.reply(plan(step("step1", "progress", new JSONObject())));
        AgentResult result = controller.handleRequest(course.getId(), null, "Show progress.");
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertEquals(1, result.getResults().size());
        assertTrue(result.getFinalAnswer().contains("Reviews completed: 0"));
        assertTrue(result.getFinalAnswer().contains("Conversation could not be saved"));
    }
}
