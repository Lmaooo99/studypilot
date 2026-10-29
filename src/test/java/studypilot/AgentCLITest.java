package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgentCLITest {
    private MockLLMClient llm;
    private Course course;
    private CLI cli;
    private ByteArrayOutputStream output;

    @Before
    public void setUp() throws Exception {
        llm = new MockLLMClient();
        AgentFacade facade = new AgentFacade(new AppConfig(Files.createTempDirectory("studypilot-agent-cli-")), llm);
        course = facade.createCourse("Design");
        output = new ByteArrayOutputStream();
        cli = new CLI(facade, new PrintStream(output), new PrintStream(new ByteArrayOutputStream()));
    }

    @Test
    public void printsOneStructuredJsonResultWithoutTextHeaders() {
        llm.reply("{\"steps\":[{\"stepId\":\"step1\",\"toolName\":\"progress\",\"arguments\":{}}]}");
        assertEquals(0, cli.runCommand(new String[] {"agent", "Show progress.", "--course", course.getId(), "--json"}));
        JSONObject result = new JSONObject(output.toString());
        assertEquals("OK", result.getString("status"));
        assertFalse(result.getBoolean("needsClarification"));
        assertEquals(1, result.getJSONArray("plan").length());
        assertEquals(1, result.getJSONArray("results").length());
        assertTrue(result.getString("finalAnswer").contains("Reviews completed: 0"));
    }

    @Test
    public void rejectsMissingScopeRepeatedFlagsAndInvalidSessionIds() {
        assertEquals(2, cli.runCommand(new String[] {"agent", "Show progress."}));
        assertEquals(2, cli.runCommand(new String[] {"agent", "Show progress.", "--course", course.getId(), "--json", "--json"}));
        assertEquals(2, cli.runCommand(new String[] {"agent", "Show progress.", "--course", course.getId(), "--session", "../invalid"}));
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void returnsFailureStatusAndJsonAfterPlanningFails() {
        llm.failNext();
        llm.failNext();
        assertEquals(1, cli.runCommand(new String[] {"agent", "Show progress.", "--course", course.getId(), "--json"}));
        JSONObject result = new JSONObject(output.toString());
        assertEquals("FAILED", result.getString("status"));
        assertEquals(0, result.getJSONArray("results").length());
        assertTrue(result.getString("finalAnswer").contains("after one retry"));
    }
}
