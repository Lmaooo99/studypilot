package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CLITest {
    private CLI cli;
    private ByteArrayOutputStream output;
    private ByteArrayOutputStream errors;

    @Before
    public void setUp() throws Exception {
        output = new ByteArrayOutputStream();
        errors = new ByteArrayOutputStream();
        Path directory = Files.createTempDirectory("studypilot-cli-test-");
        AppConfig config = new AppConfig(directory);
        AgentFacade facade = new AgentFacade(config);
        cli = new CLI(facade, new PrintStream(output), new PrintStream(errors));
    }

    @Test
    public void showsHelpWithoutAnApiKey() {
        assertEquals(0, cli.runCommand(new String[] {"help"}));
        assertTrue(output.toString().contains("gui"));
        assertEquals("", errors.toString());
    }

    @Test
    public void reportsAnUnknownCommandAsAnInputError() {
        assertEquals(2, cli.runCommand(new String[] {"missing-command"}));
        assertTrue(errors.toString().contains("Unknown command"));
    }

    @Test
    public void createsACourseAndImportsMaterialThroughTheFacade() throws Exception {
        assertEquals(0, cli.runCommand(new String[] {"course", "add", "Design"}));
        String id = output.toString().split("\\R")[0].substring("Course ID: ".length());
        Path file = Files.createTempFile("studypilot-cli-notes-", ".md");
        Files.writeString(file, "A facade provides one entry point.");
        assertEquals(0, cli.runCommand(new String[] {"material", "import", file.toString(), "--course", id}));
        assertTrue(output.toString().contains("Chunks: 1"));
    }

    @Test
    public void requiresAnExplicitCourseForMaterialCommands() {
        assertEquals(2, cli.runCommand(new String[] {"material", "list"}));
        assertTrue(errors.toString().contains("Required option: --course"));
    }

    @Test
    public void reportsMissingCommandArgumentsWithoutThrowingAnException() {
        for (String command : new String[] {"course", "material"}) {
            assertEquals(2, cli.runCommand(new String[] {command}));
        }
        assertTrue(errors.toString().contains("Unknown command"));
    }
}
