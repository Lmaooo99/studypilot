package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecoverySafetyTest {
    private static class FailingProgressRepository extends ProgressRepository {
        private boolean blocked;

        public FailingProgressRepository(Path directory) {
            super(directory);
        }

        public void saveSummaries(Map<String, ProgressSummary> summaries) {
            if (blocked) {
                throw new IllegalStateException("Test progress save failure.");
            }
            super.saveSummaries(summaries);
        }

        public void saveHistory(List<StudyEvent> events) {
            if (blocked) {
                throw new IllegalStateException("Test history save failure.");
            }
            super.saveHistory(events);
        }
    }

    private AppConfig config;
    private Path directory;
    private AgentFacade facade;
    private Course course;
    private Flashcard card;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-recovery-safety-"));
        directory = config.getDataDirectory();
        facade = new AgentFacade(config, new MockLLMClient());
        course = facade.createCourse("Design");
        card = new Flashcard("Who receives updates?", "Observers.", "Observer");
        course.setFlashcards(Arrays.asList(card));
        new CourseRepository(directory).saveCourse(course);
    }

    @Test
    public void retriesPendingCachesEvenWhenACountedAttemptIsUnreadable() throws Exception {
        FailingProgressRepository progress = new FailingProgressRepository(directory);
        ProgressTracker tracker = new ProgressTracker(progress);
        HistoryRecorder history = new HistoryRecorder(tracker, progress);
        tracker.attach(history);
        QuizRepository quizzes = new QuizRepository(directory);
        QuizService quizService = new QuizService(new MockLLMClient(), new PromptTemplates(),
                new ResponseParser(), quizzes, new TfIdfRetrieval(), tracker);
        Question question = new TrueFalseQuestion("Observers receive updates.", "Observer", true);
        Quiz quiz = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, Arrays.asList(question));
        quizzes.saveQuiz(quiz);
        QuizAttempt attempt = quizService.startAttempt(course.getId(), quiz.getId());
        Map<String, String> answers = new HashMap<>();
        answers.put(question.getId(), "true");
        quizService.submitAttempt(course.getId(), attempt.getId(), answers);
        Path source = directory.resolve("attempts").resolve(attempt.getId() + ".json");
        Files.writeString(source, "{broken");
        progress.blocked = true;
        CourseRepository courses = new CourseRepository(directory);
        FlashcardService cards = new FlashcardService(new MockLLMClient(), new PromptTemplates(),
                new ResponseParser(), courses, tracker);
        cards.review(courses.loadCourse(course.getId()), card, true);
        assertEquals(1, tracker.getSummary(course.getId()).getReviewsDone());
        assertEquals(0, progress.loadSummaries().get(course.getId()).getReviewsDone());
        assertEquals(1, progress.loadHistory().size());

        progress.blocked = false;
        ProgressRestorer restorer = new ProgressRestorer(tracker, history, progress, quizzes,
                new StudyPlanRepository(directory));
        restorer.restore(courses.loadCourse(course.getId()));
        assertEquals(1, progress.loadSummaries().get(course.getId()).getReviewsDone());
        assertEquals(2, progress.loadHistory().size());
        assertEquals("{broken", Files.readString(source));
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals(1, restarted.getProgress(course.getId()).getReviewsDone());
        assertEquals(2, restarted.getHistory(course.getId()).size());
    }

    @Test
    public void anotherCoursesPendingSaveDoesNotReportAnUnchangedCourseAsRebuilt() {
        FailingProgressRepository progress = new FailingProgressRepository(directory);
        ProgressTracker tracker = new ProgressTracker(progress);
        HistoryRecorder history = new HistoryRecorder(tracker, progress);
        tracker.attach(history);
        progress.blocked = true;
        StudyTask task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read notes.", 10);
        task.markDone();
        tracker.recordTaskDone(course.getId(), task);
        Course other = new Course("Other");
        ProgressRestorer restorer = new ProgressRestorer(tracker, history, progress,
                new QuizRepository(directory), new StudyPlanRepository(directory));
        restorer.restore(other);
        assertFalse(progress.getRecoveryWarning().contains("Progress was rebuilt for course " + other.getId()));
        progress.blocked = false;
        restorer.restore(other);
        assertEquals(1, progress.loadSummaries().get(course.getId()).getTasksDone());
        assertFalse(progress.getRecoveryWarning().contains("Progress was rebuilt for course " + other.getId()));
    }

    @Test
    public void aSavedReviewSucceedsWhileBothDerivedFilesAreBlocked() throws Exception {
        Path progress = directory.resolve("progress.json");
        Path history = directory.resolve("history.json");
        Files.createDirectory(progress);
        Files.createDirectory(history);
        facade.reviewCard(course.getId(), card.getId(), true);
        assertEquals(1, facade.getProgress(course.getId()).getReviewsDone());
        assertEquals(1, facade.getHistory(course.getId()).size());
        assertEquals(1, new CourseRepository(directory).loadCourse(course.getId()).getTotalReviews());
        assertTrue(Files.isDirectory(progress));
        assertTrue(Files.isDirectory(history));
        assertTrue(facade.getStorageWarnings().contains("Study records are saved"));
        Files.delete(progress);
        Files.delete(history);
        facade.getProgress(course.getId());
        facade.getHistory(course.getId());
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals(1, restarted.getProgress(course.getId()).getReviewsDone());
        assertEquals(1, restarted.getHistory(course.getId()).size());
    }

    @Test
    public void startupRebuildsInMemoryWithoutReplacingBlockedPaths() throws Exception {
        facade.reviewCard(course.getId(), card.getId(), true);
        StudyEvent original = facade.getHistory(course.getId()).get(0);
        for (String name : new String[] {"progress.json", "history.json"}) {
            Files.delete(directory.resolve(name));
            Files.createDirectory(directory.resolve(name));
        }
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals(1, restarted.getProgress(course.getId()).getReviewsDone());
        assertEquals(original.getId(), restarted.getHistory(course.getId()).get(0).getId());
        assertEquals(original.getTime(), restarted.getHistory(course.getId()).get(0).getTime());
        assertTrue(Files.isDirectory(directory.resolve("history.json")));
        assertTrue(restarted.getStorageWarnings().contains("could not be read"));
    }

    @Test
    public void dashboardRefreshReadsSnapshotsWithoutStartingRecovery() throws Exception {
        facade.reviewCard(course.getId(), card.getId(), true);
        final AgentFacade guarded = new AgentFacade(config, new MockLLMClient()) {
            public ProgressSummary getProgress(String courseId) {
                throw new AssertionError("Dashboard must not start progress recovery.");
            }
            public List<StudyEvent> getHistory(String courseId) {
                throw new AssertionError("Dashboard must not start history recovery.");
            }
        };
        Path history = directory.resolve("history.json");
        String before = Files.readString(history);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                DashboardView view = new DashboardView(guarded);
                view.setCourseId(course.getId());
                view.refresh();
                view.refresh();
            }
        });
        assertEquals(before, Files.readString(history));
    }

    @Test
    public void taskHistoryRetainsTheSavedCompletionTimeAcrossRecovery() throws Exception {
        LocalDateTime completed = LocalDateTime.of(2026, 11, 1, 9, 15);
        StudyTask task = new StudyTask("saved-task", LocalDate.now(), TaskType.READ, "Observer",
                "Read notes.", 20, true, completed);
        StudyPlan plan = new StudyPlan(course.getId(), LocalDate.now().plusDays(1), 1, Arrays.asList(task));
        new StudyPlanRepository(directory).savePlan(plan);
        Path file = directory.resolve("plans").resolve(plan.getId() + ".json");
        String before = Files.readString(file);
        StudyEvent event = facade.getHistory(course.getId()).get(0);
        assertEquals(completed, event.getTime());
        assertTrue(event.getDescription().contains("saved completion time"));
        Files.delete(directory.resolve("history.json"));
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals(completed, restarted.getHistory(course.getId()).get(0).getTime());
        assertEquals(before, Files.readString(file));
    }

    @Test
    public void repeatedCompletionDoesNotReplaceItsOriginalTime() {
        StudyTask task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read notes.", 20);
        task.markDone();
        LocalDateTime first = task.getCompletedAt();
        task.markDone();
        assertNotNull(first);
        assertEquals(first, task.getCompletedAt());
    }

    @Test
    public void aLegacyCompletedTaskKeepsItsUnknownCompletionTime() {
        StudyTask legacy = new StudyTask("legacy-task", LocalDate.now(), TaskType.READ,
                "Observer", "Read notes.", 20, true);
        legacy.markDone();
        assertNull(legacy.getCompletedAt());
    }

    @Test
    public void aMalformedCompletionTimeIsRejectedWithoutChangingTheSource() throws Exception {
        StudyTask task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read notes.", 20);
        StudyPlan plan = new StudyPlan(course.getId(), LocalDate.now().plusDays(1), 1, Arrays.asList(task));
        new StudyPlanRepository(directory).savePlan(plan);
        Path file = directory.resolve("plans").resolve(plan.getId() + ".json");
        JSONObject data = new JSONObject(Files.readString(file));
        data.getJSONArray("tasks").getJSONObject(0).put("completedAt", "not-a-date");
        Files.writeString(file, data.toString());
        String before = Files.readString(file);
        try {
            new StudyPlanRepository(directory).loadPlan(plan.getId());
            fail("Invalid completion times must not be silently accepted.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("original file was not changed"));
        }
        assertEquals(before, Files.readString(file));
    }

    @Test
    public void recoveredHistoryIsReturnedInTimeOrder() {
        ProgressRepository repository = new ProgressRepository(directory);
        ProgressTracker tracker = new ProgressTracker(repository);
        HistoryRecorder history = new HistoryRecorder(tracker, repository);
        tracker.attach(history);
        LocalDateTime earlier = LocalDateTime.of(2026, 11, 1, 8, 0);
        tracker.recordEvent(new StudyEvent("later", earlier.plusHours(1), course.getId(), "", "Later."));
        tracker.recordEvent(new StudyEvent("earlier", earlier, course.getId(), "", "Earlier."));
        assertEquals("earlier", history.getHistory(course.getId()).get(0).getId());
        assertEquals("later", history.getHistory(course.getId()).get(1).getId());
    }
}
