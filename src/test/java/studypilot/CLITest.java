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
    private AgentFacade facade;
    private MockLLMClient llm;

    @Before
    public void setUp() throws Exception {
        output = new ByteArrayOutputStream();
        errors = new ByteArrayOutputStream();
        llm = new MockLLMClient();
        Path directory = Files.createTempDirectory("studypilot-cli-test-");
        AppConfig config = new AppConfig(directory);
        facade = new AgentFacade(config, llm);
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
    public void reportsMissingCommandArgumentsWithoutThrowingAnException() {
        String[] commands = {"course", "material", "summarize", "concepts", "ask", "flashcards", "review"};
        for (String command : commands) {
            assertEquals("Incomplete command: " + command, 2, cli.runCommand(new String[] {command}));
        }
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
    public void generatesFlipsAndReviewsACardThroughCliCommands() throws Exception {
        Course course = facade.createCourse("CLI review");
        Path file = Files.createTempFile("studypilot-cli-card-", ".txt");
        Files.writeString(file, "A facade is a simple entry point.");
        Material material = facade.importMaterial(course.getId(), file.toString());
        llm.reply("{\"cards\":[{\"front\":\"What is a facade?\",\"back\":\"A simple entry point.\"}]}");
        assertEquals(0, cli.runCommand(new String[] {"flashcards", "generate", material.getId(), "--course", course.getId(), "--count", "1"}));
        String cardId = facade.getCardsToReview(course.getId()).get(0).getId();
        assertEquals(0, cli.runCommand(new String[] {"flashcards", "show", cardId, "--course", course.getId()}));
        assertTrue(output.toString().contains("Back: A simple entry point."));
        assertEquals(0, cli.runCommand(new String[] {"review", course.getId(), "--card", cardId, "--remembered", "true"}));
        assertEquals(0, cli.runCommand(new String[] {"progress", "--course", course.getId()}));
        assertTrue(output.toString().contains("Reviews completed: 1"));
        assertEquals(1, facade.getHistory(course.getId()).size());
    }

    @Test
    public void rejectsAnUnknownReviewValueWithoutRecordingProgress() {
        Course course = facade.createCourse("CLI review validation");
        assertEquals(2, cli.runCommand(new String[] {"review", course.getId(), "--card", "missing", "--remembered", "maybe"}));
        assertEquals(0, facade.getProgress(course.getId()).getReviewsDone());
    }
}
