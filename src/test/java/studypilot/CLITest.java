package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
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
        AgentFacade facade = new AgentFacade(new AppConfig(Files.createTempDirectory("studypilot-cli-test-")));
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
}
