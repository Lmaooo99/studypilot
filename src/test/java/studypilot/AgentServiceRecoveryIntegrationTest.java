package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgentServiceRecoveryIntegrationTest {
    private AppConfig config;
    private MockLLMClient llm;
    private AgentFacade facade;
    private Course course;
    private Material material;

    @Before
    public void setUp() throws Exception {
        Path workspace = Files.createTempDirectory("studypilot-agent-service-recovery-");
        config = new AppConfig(workspace);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Observer course");
        Path notes = workspace.resolve("observer.md");
        Files.writeString(notes, "Observer lets a subject notify its subscribers.");
        material = facade.importMaterial(course.getId(), notes.toString());
    }

    private JSONObject step(String id, String tool, JSONObject arguments) {
        return new JSONObject().put("stepId", id).put("toolName", tool).put("arguments", arguments);
    }

    private JSONObject summary(String id, String materialId, String style) {
        return step(id, "summarize", new JSONObject().put("materialId", materialId).put("style", style));
    }

    private String plan(JSONObject... steps) {
        JSONArray items = new JSONArray();
        for (JSONObject step : steps) {
            items.put(step);
        }
        return new JSONObject().put("steps", items).toString();
    }

    @Test
    public void repairsAForeignMaterialStepWithoutRepeatingASavedSummary() throws Exception {
        Course other = facade.createCourse("Separate Strategy course");
        Path notes = config.getDataDirectory().getParent().resolve("strategy.md");
        Files.writeString(notes, "Strategy selects an interchangeable algorithm.");
        Material foreign = facade.importMaterial(other.getId(), notes.toString());
        Path otherFile = config.getDataDirectory().resolve("courses").resolve(other.getId() + ".json");
        String otherBefore = Files.readString(otherFile);
        llm.reply(plan(summary("saved", material.getId(), "brief"), summary("wrong", foreign.getId(), "brief")));
        llm.reply("The subject notifies subscribers.");
        llm.reply(plan(summary("kept", material.getId(), "brief"), summary("fixed", "$kept.materialId", "bullet")));
        llm.reply("- The subject notifies its subscribers.");
        AgentResult result = facade.runAgent(course.getId(), null, "Summarize the selected material, then use bullet points.");
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(ResultStatus.FAILED, result.getResults().get("wrong").getStatus());
        assertFalse(result.getResults().get("wrong").canRetry());
        assertEquals(material.getId(), result.getResults().get("kept").getData().get("materialId"));
        assertTrue(result.getTrace().toString().contains("Reused saved for kept"));
        assertTrue(result.getFinalAnswer().contains("wrong - summarize [FAILED]"));
        assertEquals(4, llm.getPrompts().size());
        assertTrue(llm.getPrompts().get(2).contains("The subject notifies subscribers."));
        assertFalse(llm.getPrompts().toString().contains("Strategy selects an interchangeable algorithm."));
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals("- The subject notifies its subscribers.", restarted.getCourse(course.getId()).getMaterial(material.getId()).getSummary());
        assertEquals(otherBefore, Files.readString(otherFile));
        assertEquals(2, new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), result.getSessionId()).size());
    }

    @Test
    public void twoAgentRetriesIncludeTheSummaryServicesOwnRetryLimit() {
        llm.reply(plan(summary("summary", material.getId(), "brief")));
        for (int i = 0; i < 4; i++) {
            llm.failNext();
        }
        llm.reply("Observer notifications are saved after recovery.");
        AgentResult result = facade.runAgent(course.getId(), null, "Summarize Observer.");
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(6, llm.getPrompts().size());
        assertEquals(2, new JSONObject(result.toJson()).getJSONArray("plan").getJSONObject(0).getInt("retriesUsed"));
        assertTrue(result.getTrace().toString().contains("RETRY 2 of 2"));
        assertFalse(result.getTrace().toString().contains("REPLAN"));
        assertEquals("Observer notifications are saved after recovery.",
                new AgentFacade(config, new MockLLMClient()).getCourse(course.getId()).getMaterial(material.getId()).getSummary());
        List<Message> messages = new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), result.getSessionId());
        assertEquals(2, messages.size());
        assertEquals(result.getFinalAnswer(), messages.get(1).getText());
    }

    @Test
    public void replannedAliasesCannotRestartAnExhaustedSummaryAction() throws Exception {
        Course saved = facade.getCourse(course.getId());
        saved.getMaterial(material.getId()).setSummary("Earlier saved summary.");
        new CourseRepository(config.getDataDirectory()).saveCourse(saved);
        Path courseFile = config.getDataDirectory().resolve("courses").resolve(course.getId() + ".json");
        String before = Files.readString(courseFile);
        llm.reply(plan(summary("first", material.getId(), "brief")));
        for (int i = 0; i < 6; i++) {
            llm.failNext();
        }
        llm.reply(plan(summary("renamed", material.getId(), "brief")));
        llm.reply(plan(summary("renamedAgain", material.getId(), "brief")));
        AgentResult result = facade.runAgent(course.getId(), null, "Try summarizing again.");
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertEquals(9, llm.getPrompts().size());
        assertEquals(3, result.getResults().size());
        assertTrue(result.getTrace().toString().contains("REPLAN 2 of 2"));
        assertTrue(result.getTrace().toString().contains("decision ABORT"));
        assertTrue(result.getTrace().toString().contains("no tool call repeated"));
        assertEquals(before, Files.readString(courseFile));
        assertEquals(2, new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), result.getSessionId()).size());
    }

    @Test
    public void repairsInvalidQuizArgumentsBeforeSavingOneTypedQuiz() {
        JSONObject arguments = new JSONObject().put("topic", "Observer").put("count", "0").put("types", "true-false");
        llm.reply(plan(step("unused", "progress", new JSONObject()), step("quiz", "quiz", arguments)));
        arguments.put("count", "1");
        llm.reply(plan(step("quiz", "quiz", arguments)));
        JSONObject question = new JSONObject().put("type", "TRUE_FALSE").put("text", "A subject notifies subscribers.")
                .put("correctAnswer", "true").put("conceptName", "Observer");
        llm.reply(new JSONObject().put("questions", new JSONArray().put(question)).toString());
        AgentResult result = facade.runAgent(course.getId(), null, "Create one true/false question about Observer.");
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals(3, llm.getPrompts().size());
        assertFalse(result.getResults().containsKey("unused"));
        String quizId = result.getResults().get("quiz").getData().get("quizId");
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals(1, restarted.listQuizzes(course.getId()).size());
        assertEquals(QuestionType.TRUE_FALSE, restarted.getQuiz(course.getId(), quizId).getQuestions().get(0).getType());
        assertEquals(0, restarted.getProgress(course.getId()).getQuizzesTaken());
    }

    @Test
    public void aClarifiedRequestDoesNotLeakItsResultsIntoTheNextTurn() {
        llm.reply(plan(step("first", "weakTopics", new JSONObject()),
                step("notRun", "quiz", new JSONObject().put("topic", "Observer"))));
        AgentResult first = facade.runAgent(course.getId(), null, "Find a weak topic and quiz me.");
        assertEquals(ResultStatus.INSUFFICIENT_DATA, first.getStatus());
        assertTrue(first.needsClarification());
        assertTrue(facade.listQuizzes(course.getId()).isEmpty());
        llm.reply(plan(step("first", "progress", new JSONObject())));
        AgentResult second = facade.runAgent(course.getId(), first.getSessionId(), "Show progress instead.");
        assertEquals(ResultStatus.OK, second.getStatus());
        assertFalse(second.needsClarification());
        assertEquals(1, second.getResults().size());
        assertTrue(second.getFinalAnswer().contains("first - progress [OK]"));
        assertFalse(second.getTrace().toString().contains("CLARIFY"));
        assertEquals(2, llm.getPrompts().size());
        assertTrue(llm.getPrompts().get(1).contains(first.getFinalAnswer()));
        assertEquals(4, new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), first.getSessionId()).size());
    }

    @Test
    public void partialSuccessSurvivesPlanningFailureAndAConversationRestart() {
        String missingId = UUID.randomUUID().toString();
        llm.reply(plan(summary("saved", material.getId(), "brief"), summary("missing", missingId, "brief")));
        llm.reply("This summary survives the later failure.");
        llm.failNext();
        llm.failNext();
        AgentResult first = facade.runAgent(course.getId(), null, "Summarize both materials.");
        assertEquals(ResultStatus.FAILED, first.getStatus());
        assertEquals(ResultStatus.OK, first.getResults().get("saved").getStatus());
        assertEquals(4, llm.getPrompts().size());
        MockLLMClient reader = new MockLLMClient();
        reader.reply(plan(step("saved", "progress", new JSONObject())));
        AgentFacade restarted = new AgentFacade(config, reader);
        AgentResult second = restarted.runAgent(course.getId(), first.getSessionId(), "Keep the saved work and show progress.");
        assertEquals(ResultStatus.OK, second.getStatus());
        assertEquals(1, second.getResults().size());
        assertTrue(reader.getPrompts().get(0).contains(first.getFinalAnswer()));
        assertFalse(second.getTrace().toString().contains("REPLAN"));
        assertEquals("This summary survives the later failure.", restarted.getCourse(course.getId()).getMaterial(material.getId()).getSummary());
        assertEquals(4, new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), first.getSessionId()).size());
    }

    @Test
    public void aNewRequestCannotResolveReferencesFromAnEarlierRequestsResults() throws Exception {
        JSONObject arguments = new JSONObject().put("topic", "Observer").put("count", "1").put("types", "true-false");
        llm.reply(plan(step("created", "quiz", arguments)));
        JSONObject question = new JSONObject().put("type", "TRUE_FALSE").put("text", "A subject notifies subscribers.")
                .put("correctAnswer", "true").put("conceptName", "Observer");
        llm.reply(new JSONObject().put("questions", new JSONArray().put(question)).toString());
        AgentResult first = facade.runAgent(course.getId(), null, "Create an Observer quiz.");
        String quizId = first.getResults().get("created").getData().get("quizId");
        Path quizFile = config.getDataDirectory().resolve("quizzes").resolve(quizId + ".json");
        String before = Files.readString(quizFile);
        arguments.put("topic", "$created.topic");
        llm.reply(plan(step("dependent", "quiz", arguments)));
        llm.reply("{\"steps\":[],\"clarification\":\"Which topic should this new request use?\"}");
        AgentResult second = facade.runAgent(course.getId(), first.getSessionId(), "Create another quiz.");
        assertEquals(ResultStatus.INSUFFICIENT_DATA, second.getStatus());
        assertTrue(second.getResults().isEmpty());
        assertTrue(second.getTrace().toString().contains("earlier step or a completed result"));
        assertFalse(second.getTrace().toString().contains("Calling dependent"));
        assertEquals(4, llm.getPrompts().size());
        assertEquals(1, facade.listQuizzes(course.getId()).size());
        assertEquals(before, Files.readString(quizFile));
        assertEquals(4, new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), first.getSessionId()).size());
    }

    @Test
    public void anActualConversationWriteFailureKeepsTheSavedToolResult() throws Exception {
        Path blocked = config.getDataDirectory().resolve("conversations");
        Files.writeString(blocked, "Keep this existing file.");
        llm.reply(plan(summary("summary", material.getId(), "brief")));
        llm.reply("The real summary service saved this result.");
        AgentResult result = facade.runAgent(course.getId(), null, "Summarize Observer.");
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertEquals(ResultStatus.OK, result.getResults().get("summary").getStatus());
        assertTrue(result.getFinalAnswer().contains("Conversation could not be saved"));
        assertEquals("Keep this existing file.", Files.readString(blocked));
        assertEquals("The real summary service saved this result.",
                new AgentFacade(config, new MockLLMClient()).getCourse(course.getId()).getMaterial(material.getId()).getSummary());
        assertEquals(2, llm.getPrompts().size());
    }
}
