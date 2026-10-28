package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudyPlanGeneratorTest {
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private LocalDate today;
    private LocalDate exam;

    @Before
    public void setUp() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-plan-test-");
        config = new AppConfig(directory);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        Path notes = directory.resolve("notes.md");
        Files.writeString(notes, "Observer lets a subject notify its subscribers when state changes.");
        facade.importMaterial(course.getId(), notes.toString());
        today = LocalDate.now();
        exam = today.plusDays(2);
    }

    private JSONObject task(LocalDate date, String type, int minutes) {
        JSONObject data = new JSONObject();
        data.put("date", date.toString());
        data.put("type", type);
        data.put("conceptName", "Observer");
        data.put("description", "Review subjects and subscribers.");
        data.put("minutes", minutes);
        return data;
    }

    private String proposal(JSONObject... tasks) {
        JSONArray items = new JSONArray();
        for (JSONObject task : tasks) {
            items.put(task);
        }
        return new JSONObject().put("tasks", items).toString();
    }

    @Test
    public void generatesAndRestoresDatedTasksFromMaterial() {
        llm.reply(proposal(task(exam, "READ", 30), task(today, "DIAGNOSTIC_QUIZ", 15)));
        StudyPlan plan = facade.createStudyPlan(course.getId(), exam, 1);
        StudyPlan restored = facade.getStudyPlan(course.getId(), plan.getId());
        assertEquals(plan.getId(), restored.getId());
        assertEquals(plan.getTasks().get(0).getId(), restored.getTasks().get(0).getId());
        assertEquals(TaskType.DIAGNOSTIC_QUIZ, restored.getTasks().get(0).getType());
        assertEquals(today, restored.getTasks().get(0).getDate());
        assertEquals(15, restored.getTotalMinutes(today));
        assertEquals(30, restored.getTotalMinutes(exam));
        assertEquals(1, llm.getPrompts().size());
        assertTrue(llm.getPrompts().get(0).contains("subject notify its subscribers"));
        assertFalse(restored.getTasks().get(0).isDone());
        assertEquals(0, facade.getProgress(course.getId()).getTasksDone());
    }

    @Test
    public void repairsOutOfRangeDatesWithinTheProposalLimit() {
        llm.reply(proposal(task(today.minusDays(1), "DIAGNOSTIC_QUIZ", 15)));
        llm.reply(proposal(task(exam.plusDays(1), "DIAGNOSTIC_QUIZ", 15)));
        llm.reply(proposal(task(today, "DIAGNOSTIC_QUIZ", 15)));
        StudyPlan plan = facade.createStudyPlan(course.getId(), exam, 1);
        assertEquals(today, plan.getTasks().get(0).getDate());
        assertEquals(3, llm.getPrompts().size());
        assertTrue(llm.getPrompts().get(1).contains("Fix these issues"));
        assertEquals(1, facade.listStudyPlans(course.getId()).size());
    }

    @Test
    public void reducesAnOverBudgetProposalAndKeepsTheNotice() {
        llm.reply(proposal(task(today, "DIAGNOSTIC_QUIZ", 40), task(today, "READ", 40)));
        llm.reply(proposal(task(today, "DIAGNOSTIC_QUIZ", 15), task(today, "READ", 30)));
        StudyPlan plan = facade.createStudyPlan(course.getId(), exam, 1);
        assertEquals(45, plan.getTotalMinutes(today));
        assertEquals(2, llm.getPrompts().size());
        assertTrue(llm.getPrompts().get(1).contains("Reduce task lengths or scope"));
        assertTrue(facade.getStudyPlan(course.getId(), plan.getId()).getNotice().contains("daily budget"));
    }

    @Test
    public void requiresADiagnosticFirstWhenGradedHistoryIsInsufficient() {
        for (int i = 0; i < 3; i++) {
            llm.reply(proposal(task(today, "READ", 30)));
        }
        try {
            facade.createStudyPlan(course.getId(), exam, 1);
            fail("A plan without history must start with a diagnostic quiz.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("DIAGNOSTIC_QUIZ"));
        }
        assertEquals(3, llm.getPrompts().size());
        assertTrue(facade.listStudyPlans(course.getId()).isEmpty());
    }

    @Test
    public void usesMeasuredTopicsWithoutCallingTheExplanationService() {
        List<Question> questions = new ArrayList<>();
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            Question question = new TrueFalseQuestion("Observer statement " + i, "Observer", true);
            questions.add(question);
            answers.put(question.getId(), "false");
        }
        Quiz quiz = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        facade.submitAttempt(course.getId(), attempt.getId(), answers);
        llm.reply(proposal(task(today, "READ", 30)));
        StudyPlan plan = facade.createStudyPlan(course.getId(), exam, 1);
        assertEquals(TaskType.READ, plan.getTasks().get(0).getType());
        assertEquals(1, llm.getPrompts().size());
        assertTrue(llm.getPrompts().get(0).contains("Observer: 0/5 correct"));
    }

    @Test
    public void rejectsPastDatesAndInvalidHoursBeforeCallingTheModel() {
        try {
            facade.createStudyPlan(course.getId(), today.minusDays(1), 1);
            fail("A past exam date must be rejected.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("exam date"));
        }
        int[] invalidHours = {0, -1, 25, Integer.MAX_VALUE};
        for (int hours : invalidHours) {
            try {
                facade.createStudyPlan(course.getId(), exam, hours);
                fail("Invalid hours must be rejected.");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Hours per day"));
            }
        }
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void rejectsAnEmptyCourseBeforeCallingTheModel() {
        Course empty = facade.createCourse("Empty");
        try {
            facade.createStudyPlan(empty.getId(), exam, 1);
            fail("Material is required for a grounded plan.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Import course material"));
        }
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void stopsAfterThreeFailedRepliesWithoutSavingAFallbackPlan() {
        llm.failNext();
        llm.reply("not JSON");
        llm.reply(proposal());
        try {
            facade.createStudyPlan(course.getId(), exam, 1);
            fail("Invalid proposals must not be saved.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("3 proposal attempts"));
        }
        assertEquals(3, llm.getPrompts().size());
        assertTrue(facade.listStudyPlans(course.getId()).isEmpty());
    }

    @Test
    public void rejectsCoercedFractionalAndNonpositiveMinutes() {
        Object[] invalid = {"20", 20.5, 0, -1};
        for (Object minutes : invalid) {
            JSONObject item = task(today, "DIAGNOSTIC_QUIZ", 20).put("minutes", minutes);
            try {
                new ResponseParser().parseStudyTasks(proposal(item));
                fail("Task minutes must be a positive JSON integer.");
            } catch (IllegalArgumentException | JSONException e) {
                assertTrue(e.getMessage().contains("minutes"));
            }
        }
    }

    @Test
    public void allowsAnExamTodayWhenTheTasksFit() {
        llm.reply(proposal(task(today, "DIAGNOSTIC_QUIZ", 60)));
        StudyPlan plan = facade.createStudyPlan(course.getId(), today, 1);
        assertEquals(60, plan.getTotalMinutes(today));
        assertEquals(today, plan.getExamDate());
    }
}
