package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StructuredAgentCLITest {
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private Material material;
    private CLI cli;
    private ByteArrayOutputStream output;
    private ByteArrayOutputStream errors;

    @Before
    public void setUp() throws Exception {
        Path workspace = Files.createTempDirectory("studypilot-structured-cli-");
        config = new AppConfig(workspace);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Observer course");
        Path notes = workspace.resolve("observer.md");
        Files.writeString(notes, "A subject notifies its subscribers.");
        material = facade.importMaterial(course.getId(), notes.toString());
        output = new ByteArrayOutputStream();
        errors = new ByteArrayOutputStream();
        cli = new CLI(facade, new PrintStream(output), new PrintStream(errors));
    }

    private JSONObject step(String id, String tool, JSONObject arguments) {
        return new JSONObject().put("stepId", id).put("toolName", tool).put("arguments", arguments);
    }

    private JSONObject summary(String id, String materialId, String style) {
        return step(id, "summarize", new JSONObject().put("materialId", materialId).put("style", style));
    }

    private void plan(JSONObject... steps) {
        JSONArray items = new JSONArray();
        for (JSONObject step : steps) {
            items.put(step);
        }
        llm.reply(new JSONObject().put("steps", items).toString());
    }

    private int run(String request) {
        return cli.runCommand(new String[] {"agent", request, "--course", course.getId(), "--json"});
    }

    private JSONObject jsonResult() {
        String text = output.toString().trim();
        assertEquals(1, text.split("\\R").length);
        assertTrue(text.startsWith("{") && text.endsWith("}"));
        JSONObject result = new JSONObject(text);
        String session = result.getString("sessionId");
        assertEquals(session, UUID.fromString(session).toString());
        return result;
    }

    @Test
    public void summaryJsonPreservesEscapesUnicodeAndSavedMessages() {
        String request = "Explain \"Observer\".\nUse C:\\notes\\observer.";
        String answer = "Quoted \"Observer\".\nPath C:\\notes.\nSymbols: \u03A9 and \uD83D\uDE80.";
        plan(summary("saved", material.getId(), "brief"));
        llm.reply(answer);
        assertEquals(0, run(request));
        JSONObject result = jsonResult();
        assertEquals("OK", result.getString("status"));
        assertFalse(result.getBoolean("needsClarification"));
        assertEquals(1, result.getJSONArray("plan").length());
        JSONObject planned = result.getJSONArray("plan").getJSONObject(0);
        JSONObject actual = result.getJSONArray("results").getJSONObject(0);
        assertEquals("saved", planned.getString("stepId"));
        assertEquals("saved", actual.getString("stepId"));
        assertEquals("summarize", actual.getString("toolName"));
        assertEquals(material.getId(), planned.getJSONObject("arguments").getString("materialId"));
        assertEquals(material.getId(), actual.getJSONObject("data").getString("materialId"));
        assertEquals(0, planned.getInt("retriesUsed"));
        assertEquals(answer, actual.getString("output"));
        assertEquals("", actual.getString("errorMessage"));
        assertFalse(actual.getBoolean("retryable"));
        List<Message> saved = new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), result.getString("sessionId"));
        assertEquals(request, saved.get(0).getText());
        assertEquals(result.getString("finalAnswer"), saved.get(1).getText());
        assertEquals(answer, facade.getCourse(course.getId()).getMaterial(material.getId()).getSummary());
        assertEquals("", errors.toString());
    }

    @Test
    public void plannerClarificationReturnsZeroWithEmptyPlanAndResults() {
        llm.reply("{\"steps\":[],\"clarification\":\"Which topic should I use?\"}");
        assertEquals(0, run("Help me study."));
        JSONObject result = jsonResult();
        assertEquals("INSUFFICIENT_DATA", result.getString("status"));
        assertTrue(result.getBoolean("needsClarification"));
        assertEquals(0, result.getJSONArray("plan").length());
        assertEquals(0, result.getJSONArray("results").length());
        assertTrue(result.getString("finalAnswer").contains("Which topic should I use?"));
        assertTrue(result.getJSONArray("trace").toString().contains("CLARIFY"));
        assertEquals("", errors.toString());
    }

    @Test
    public void toolClarificationIncludesOnlyTheStepsActuallyEncountered() {
        plan(step("counts", "progress", new JSONObject()), step("weak", "weakTopics", new JSONObject()),
                step("notRun", "quiz", new JSONObject().put("topic", "Observer")));
        assertEquals(0, run("Show progress, find a weak topic and quiz me."));
        JSONObject result = jsonResult();
        assertEquals("INSUFFICIENT_DATA", result.getString("status"));
        assertEquals(2, result.getJSONArray("plan").length());
        JSONArray actual = result.getJSONArray("results");
        assertEquals(2, actual.length());
        assertEquals("counts", actual.getJSONObject(0).getString("stepId"));
        assertEquals("0", actual.getJSONObject(0).getJSONObject("data").getString("gradedCount"));
        assertEquals("weak", actual.getJSONObject(1).getString("stepId"));
        assertEquals("INSUFFICIENT_DATA", actual.getJSONObject(1).getString("status"));
        assertFalse(actual.getJSONObject(1).getBoolean("retryable"));
        assertTrue(result.getBoolean("needsClarification"));
        assertTrue(facade.listQuizzes(course.getId()).isEmpty());
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void partialFailureReturnsOneWithSuccessfulAndFailedOutcomes() {
        String missing = UUID.randomUUID().toString();
        plan(step("counts", "progress", new JSONObject()), summary("missing", missing, "brief"),
                step("notRun", "progress", new JSONObject()));
        llm.failNext();
        llm.failNext();
        assertEquals(1, run("Show progress and summarize both files."));
        JSONObject result = jsonResult();
        assertEquals("FAILED", result.getString("status"));
        assertFalse(result.getBoolean("needsClarification"));
        JSONArray actual = result.getJSONArray("results");
        assertEquals(2, actual.length());
        assertEquals("OK", actual.getJSONObject(0).getString("status"));
        assertEquals("FAILED", actual.getJSONObject(1).getString("status"));
        assertFalse(actual.getJSONObject(1).getBoolean("retryable"));
        assertFalse(actual.getJSONObject(1).getString("errorMessage").isBlank());
        assertEquals(missing, result.getJSONArray("plan").getJSONObject(1).getJSONObject("arguments").getString("materialId"));
        assertTrue(result.getString("finalAnswer").contains("counts - progress [OK]"));
        assertTrue(result.getJSONArray("trace").toString().contains("ABORT: planning failed"));
        assertEquals("", errors.toString());
    }

    @Test
    public void recoveredJsonKeepsFailuresAndUsesResolvedArgumentsForTheLaterStep() {
        plan(summary("saved", material.getId(), "brief"), summary("bad", UUID.randomUUID().toString(), "brief"));
        llm.reply("The first saved summary.");
        plan(summary("kept", material.getId(), "brief"), summary("fixed", "$kept.materialId", "bullet"));
        llm.reply("- The corrected bullet summary.");
        assertEquals(0, run("Summarize the selected notes and then use bullets."));
        JSONObject result = jsonResult();
        assertEquals("OK", result.getString("status"));
        JSONArray planned = result.getJSONArray("plan");
        JSONArray actual = result.getJSONArray("results");
        assertEquals(4, planned.length());
        assertEquals(4, actual.length());
        assertEquals("FAILED", actual.getJSONObject(1).getString("status"));
        assertEquals("kept", actual.getJSONObject(2).getString("stepId"));
        assertEquals(material.getId(), planned.getJSONObject(3).getJSONObject("arguments").getString("materialId"));
        assertEquals("- The corrected bullet summary.", actual.getJSONObject(3).getString("output"));
        assertTrue(result.getJSONArray("trace").toString().contains("Reused saved for kept"));
        assertTrue(result.getJSONArray("trace").toString().contains("Resolved fixed"));
        assertEquals(4, llm.getPrompts().size());
    }

    @Test
    public void retryMetadataMatchesTheActualServiceCallsAndTrace() {
        plan(summary("summary", material.getId(), "brief"));
        llm.failNext();
        llm.failNext();
        llm.reply("The summary succeeded on the second tool call.");
        assertEquals(0, run("Summarize Observer."));
        JSONObject result = jsonResult();
        assertEquals("OK", result.getString("status"));
        assertEquals(1, result.getJSONArray("plan").getJSONObject(0).getInt("retriesUsed"));
        assertFalse(result.getJSONArray("results").getJSONObject(0).getBoolean("retryable"));
        assertTrue(result.getJSONArray("trace").toString().contains("RETRY 1 of 2"));
        assertEquals(4, llm.getPrompts().size());
    }

    @Test
    public void recoveryWarningsStayOnStderrWhileStdoutRemainsJson() throws Exception {
        Files.writeString(config.getDataDirectory().resolve("progress.json"), "{broken progress");
        cli = new CLI(new AgentFacade(config, llm), new PrintStream(output), new PrintStream(errors));
        plan(step("counts", "progress", new JSONObject()));
        assertEquals(0, run("Show progress."));
        JSONObject result = jsonResult();
        assertEquals("OK", result.getString("status"));
        assertTrue(errors.toString().contains("backed up"));
        assertFalse(output.toString().contains("backed up"));
        assertEquals("0", result.getJSONArray("results").getJSONObject(0).getJSONObject("data").getString("reviewsDone"));
    }

    @Test
    public void memorySaveFailureStillReturnsJsonWithTheCompletedToolOutput() throws Exception {
        Path blocker = config.getDataDirectory().resolve("conversations");
        Files.writeString(blocker, "Retain this regular file.");
        plan(step("counts", "progress", new JSONObject()));
        assertEquals(1, run("Show progress."));
        JSONObject result = jsonResult();
        assertEquals("FAILED", result.getString("status"));
        assertEquals("OK", result.getJSONArray("results").getJSONObject(0).getString("status"));
        assertTrue(result.getString("finalAnswer").contains("Conversation could not be saved"));
        assertTrue(result.getJSONArray("trace").toString().contains("Memory save failed"));
        assertEquals("Retain this regular file.", Files.readString(blocker));
        assertEquals("", errors.toString());
    }
}
