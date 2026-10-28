package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudyPlanRepositoryTest {
    private Path directory;
    private StudyPlanRepository repository;
    private StudyPlan plan;
    private StudyTask task;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("studypilot-plan-store-");
        repository = new StudyPlanRepository(directory);
        task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read the course notes.", 30);
        plan = new StudyPlan(UUID.randomUUID().toString(), LocalDate.now().plusDays(2), 1, Arrays.asList(task));
        repository.savePlan(plan);
    }

    @Test
    public void preservesTaskIdsTypesAndCompletionAcrossReloads() {
        task.markDone();
        repository.savePlan(plan);
        StudyPlan restored = new StudyPlanRepository(directory).loadPlan(plan.getId());
        assertEquals(plan.getId(), restored.getId());
        assertEquals(task.getId(), restored.getTasks().get(0).getId());
        assertEquals(TaskType.READ, restored.getTasks().get(0).getType());
        assertTrue(restored.getTasks().get(0).isDone());
        assertEquals(1, repository.listPlans(plan.getCourseId()).size());
        assertTrue(repository.listPlans("another-course").isEmpty());
    }

    @Test
    public void preservesACorruptPlanFileWhenLoadingFails() throws Exception {
        Path file = directory.resolve("plans").resolve(plan.getId() + ".json");
        Files.writeString(file, "{damaged plan");
        try {
            repository.loadPlan(plan.getId());
            fail("Damaged plan JSON must be rejected.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("original file was not changed"));
        }
        assertEquals("{damaged plan", Files.readString(file));
    }

    @Test
    public void rejectsSavedTaskFieldsWithTheWrongTypes() throws Exception {
        Path file = directory.resolve("plans").resolve(plan.getId() + ".json");
        JSONObject saved = new JSONObject(Files.readString(file));
        saved.getJSONArray("tasks").getJSONObject(0).put("done", "true");
        String damaged = saved.toString();
        Files.writeString(file, damaged);
        try {
            repository.loadPlan(plan.getId());
            fail("A completion flag must be a JSON boolean.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Saved plan fields"));
        }
        assertEquals(damaged, Files.readString(file));
    }

    @Test
    public void doesNotOverwriteAValidPlanWithAnOverBudgetPlan() throws Exception {
        Path file = directory.resolve("plans").resolve(plan.getId() + ".json");
        String before = Files.readString(file);
        StudyTask extra = new StudyTask(task.getDate(), TaskType.TAKE_QUIZ, "Observer", "Take a practice quiz.", 40);
        StudyPlan invalid = new StudyPlan(plan.getId(), plan.getCourseId(), plan.getExamDate(), 1,
                Arrays.asList(task, extra), "");
        try {
            repository.savePlan(invalid);
            fail("An invalid plan must not replace saved work.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("daily budget"));
        }
        assertEquals(before, Files.readString(file));
    }
}
