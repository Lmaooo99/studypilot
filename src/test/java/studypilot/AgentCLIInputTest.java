package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgentCLIInputTest {
    private AppConfig config;
    private MockLLMClient llm;
    private Course course;
    private CLI cli;
    private ByteArrayOutputStream output;
    private ByteArrayOutputStream errors;
    private Path courseFile;
    private String savedCourse;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-agent-cli-input-"));
        llm = new MockLLMClient();
        AgentFacade facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        courseFile = config.getDataDirectory().resolve("courses").resolve(course.getId() + ".json");
        savedCourse = Files.readString(courseFile);
        output = new ByteArrayOutputStream();
        errors = new ByteArrayOutputStream();
        cli = new CLI(facade, new PrintStream(output), new PrintStream(errors));
    }

    private void inputError(String[] arguments) throws Exception {
        output.reset();
        errors.reset();
        assertEquals(2, cli.runCommand(arguments));
        assertEquals("", output.toString());
        assertFalse(errors.toString().isBlank());
        assertEquals(0, llm.getPrompts().size());
        assertEquals(savedCourse, Files.readString(courseFile));
        assertFalse(Files.exists(config.getDataDirectory().resolve("conversations")));
    }

    @Test
    public void anAgentOptionCannotTakeTheMissingRequestsPlace() throws Exception {
        String[] options = {"--json", "--course", "--session"};
        for (String option : options) {
            inputError(new String[] {"agent", option, "--course", course.getId()});
            assertTrue(errors.toString().contains("Enter a study request"));
        }
    }

    @Test
    public void incompleteRepeatedAndPositionalOptionsFailBeforePlanning() throws Exception {
        String[][] invalid = {
            {"agent", "Show progress.", "--course", course.getId(), "--session"},
            {"agent", "Show progress.", "--course", course.getId(), "--session", "--json"},
            {"agent", "Show progress.", "--course", course.getId(), "--course", course.getId()},
            {"agent", "Show progress.", "--course", course.getId(), "unexpected positional value"},
            {"agent", "Show progress.", "--course", course.getId(), "--wrong", "value"}
        };
        for (String[] arguments : invalid) {
            inputError(arguments);
        }
    }

    @Test
    public void aBlankRequestReturnsAnInputErrorWithoutSavingATurn() throws Exception {
        inputError(new String[] {"agent", " \n ", "--course", course.getId(), "--json"});
        assertTrue(errors.toString().contains("Enter a study request"));
    }

    @Test
    public void jsonFlagCanPrecedeValuedOptionsAndRetainsTheRequestedSession() throws Exception {
        String session = UUID.randomUUID().toString();
        llm.reply("{\"steps\":[],\"clarification\":\"Which activity next?\"}");
        assertEquals(0, cli.runCommand(new String[] {"agent", "Help me study.", "--json", "--session", session,
                "--course", course.getId()}));
        JSONObject result = new JSONObject(output.toString());
        assertEquals(session, result.getString("sessionId"));
        assertTrue(result.getBoolean("needsClarification"));
        Path file = config.getDataDirectory().resolve("conversations").resolve(session + ".json");
        assertEquals(course.getId(), new JSONObject(Files.readString(file)).getString("courseId"));
        assertEquals(1, llm.getPrompts().size());
        assertEquals("", errors.toString());
    }

    @Test
    public void optionNamesInsideARequestRemainOrdinaryRequestText() {
        String request = "Explain --json and --session as words from my notes.";
        llm.reply("{\"steps\":[],\"clarification\":\"Which notes should I use?\"}");
        assertEquals(0, cli.runCommand(new String[] {"agent", request, "--course", course.getId(), "--json"}));
        JSONObject result = new JSONObject(output.toString());
        assertEquals("INSUFFICIENT_DATA", result.getString("status"));
        assertTrue(llm.getPrompts().get(0).contains("Student request: " + request));
        assertEquals("", errors.toString());
    }
}
