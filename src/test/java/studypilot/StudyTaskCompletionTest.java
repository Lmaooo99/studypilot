package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudyTaskCompletionTest {
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private StudyPlan plan;
    private StudyTask task;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-complete-task-"));
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read the Observer notes.", 30);
        plan = new StudyPlan(course.getId(), LocalDate.now().plusDays(2), 1, Arrays.asList(task));
        new StudyPlanRepository(config.getDataDirectory()).savePlan(plan);
    }

    @Test
    public void countsACompletionOnceAcrossRepeatedActionsAndRestarts() {
        facade.completeTask(course.getId(), plan.getId(), task.getId());
        facade.completeTask(course.getId(), plan.getId(), task.getId());
        AgentFacade restarted = new AgentFacade(config, llm);
        restarted.completeTask(course.getId(), plan.getId(), task.getId());
        assertTrue(restarted.getStudyPlan(course.getId(), plan.getId()).getTask(task.getId()).isDone());
        assertEquals(1, restarted.getProgress(course.getId()).getTasksDone());
        assertEquals(1, restarted.getHistory(course.getId()).size());
        assertEquals(0, restarted.getProgress(course.getId()).getGradedCount());
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void rejectsAccessAndCompletionFromAnotherCourse() {
        Course other = facade.createCourse("Other");
        try {
            facade.completeTask(other.getId(), plan.getId(), task.getId());
            fail("A task must remain in its course.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("this course"));
        }
        assertFalse(facade.getStudyPlan(course.getId(), plan.getId()).getTask(task.getId()).isDone());
        assertEquals(0, facade.getProgress(course.getId()).getTasksDone());
        assertTrue(facade.listStudyPlans(other.getId()).isEmpty());
    }

    @Test
    public void rejectsATaskThatDoesNotBelongToThePlan() {
        try {
            facade.completeTask(course.getId(), plan.getId(), "missing-task");
            fail("Only a task in this plan can be completed.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not found in this plan"));
        }
        assertEquals(0, facade.getProgress(course.getId()).getTasksDone());
        assertTrue(facade.getHistory(course.getId()).isEmpty());
    }

    @Test
    public void keepsTheTaskUndoneIfSavingThePlanFails() throws Exception {
        StudyPlanRepository failing = new StudyPlanRepository(config.getDataDirectory()) {
            public void savePlan(StudyPlan value) {
                throw new IllegalStateException("Test plan save failure.");
            }
        };
        ProgressTracker tracker = new ProgressTracker(new ProgressRepository(config.getDataDirectory()));
        StudyPlanGenerator generator = new StudyPlanGenerator(new CourseService(new CourseRepository(config.getDataDirectory())),
                tracker, llm, new PromptTemplates(), new ResponseParser(), failing, 5);
        try {
            generator.markTaskDone(course.getId(), plan.getId(), task.getId());
            fail("A plan save failure must be reported.");
        } catch (IllegalStateException e) {
            assertEquals("Test plan save failure.", e.getMessage());
        }
        assertFalse(facade.getStudyPlan(course.getId(), plan.getId()).getTask(task.getId()).isDone());
        assertEquals(0, tracker.getSummary(course.getId()).getTasksDone());
    }

    @Test
    public void retriesAFailedProgressSaveWithoutRepeatingTheCount() throws Exception {
        Path file = config.getDataDirectory().resolve("progress.json");
        Files.createDirectory(file);
        try {
            facade.completeTask(course.getId(), plan.getId(), task.getId());
            fail("A failed progress save must be reported.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("could not be saved"));
        }
        assertTrue(facade.getStudyPlan(course.getId(), plan.getId()).getTask(task.getId()).isDone());
        Files.delete(file);
        AgentFacade restarted = new AgentFacade(config, llm);
        restarted.completeTask(course.getId(), plan.getId(), task.getId());
        assertEquals(1, restarted.getProgress(course.getId()).getTasksDone());
        assertEquals(1, restarted.getHistory(course.getId()).size());
    }

    @Test
    public void retriesAFailedHistorySaveWithoutRepeatingTheCount() throws Exception {
        Path file = config.getDataDirectory().resolve("history.json");
        Files.createDirectory(file);
        try {
            facade.completeTask(course.getId(), plan.getId(), task.getId());
            fail("A failed history save must be reported.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("could not be saved"));
        }
        Files.delete(file);
        AgentFacade restarted = new AgentFacade(config, llm);
        restarted.completeTask(course.getId(), plan.getId(), task.getId());
        restarted.completeTask(course.getId(), plan.getId(), task.getId());
        assertEquals(1, restarted.getProgress(course.getId()).getTasksDone());
        assertEquals(1, restarted.getHistory(course.getId()).size());
    }
}
