package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudyPlanCLITest {
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private CLI cli;
    private ByteArrayOutputStream output;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-plan-cli-"));
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        output = new ByteArrayOutputStream();
        cli = new CLI(facade, new PrintStream(output), new PrintStream(new ByteArrayOutputStream()));
    }

    @Test
    public void listsAnEmptyCourseWithoutCallingTheModel() {
        assertEquals(0, cli.runCommand(new String[] {"plan", "list", "--course", course.getId()}));
        assertTrue(output.toString().contains("No saved study plans"));
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void rejectsMissingInputsInvalidDatesAndUnknownFlags() {
        assertEquals(2, cli.runCommand(new String[] {"plan"}));
        assertEquals(2, cli.runCommand(new String[] {"plan", "--course", course.getId(),
                "--exam", "invalid", "--hours", "1"}));
        assertEquals(2, cli.runCommand(new String[] {"plan", "--course", course.getId(),
                "--exam", LocalDate.now().toString(), "--hours", "1.5"}));
        assertEquals(2, cli.runCommand(new String[] {"plan", "list", "--course", course.getId(), "--unknown", "value"}));
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void showsAndCompletesASavedTaskAcrossSeparateFacades() {
        StudyTask task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read the notes.", 20);
        StudyPlan plan = new StudyPlan(course.getId(), LocalDate.now(), 1, Arrays.asList(task));
        new StudyPlanRepository(config.getDataDirectory()).savePlan(plan);
        assertEquals(0, cli.runCommand(new String[] {"plan", "show", plan.getId(), "--course", course.getId()}));
        assertTrue(output.toString().contains(task.getId()));
        assertEquals(0, cli.runCommand(new String[] {"plan", "complete", plan.getId(), task.getId(), "--course", course.getId()}));
        AgentFacade restarted = new AgentFacade(config, llm);
        CLI afterRestart = new CLI(restarted, new PrintStream(output), new PrintStream(new ByteArrayOutputStream()));
        assertEquals(0, afterRestart.runCommand(new String[] {"plan", "complete", plan.getId(), task.getId(), "--course", course.getId()}));
        assertEquals(1, restarted.getProgress(course.getId()).getTasksDone());
        assertEquals(1, restarted.getHistory(course.getId()).size());
        assertEquals(0, llm.getPrompts().size());
    }
}
