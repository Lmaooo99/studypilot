package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressRecoveryTest {
    private Path directory;
    private AppConfig config;
    private MockLLMClient llm;
    private AgentFacade facade;
    private Course course;
    private Flashcard card;
    private QuizAttempt attempt;
    private StudyPlan plan;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-progress-recovery-"));
        directory = config.getDataDirectory();
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        card = new Flashcard("Who receives notifications?", "Observers.", "Observer");
        course.setFlashcards(Arrays.asList(card));
        new CourseRepository(directory).saveCourse(course);
        saveAttempt();
        StudyTask task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read the notes.", 20);
        task.markDone();
        plan = new StudyPlan(course.getId(), LocalDate.now().plusDays(1), 1, Arrays.asList(task));
        new StudyPlanRepository(directory).savePlan(plan);
    }

    private void saveAttempt() {
        Question objective = new TrueFalseQuestion("Observers receive notifications.", "Observer", true);
        Question shortAnswer = new ShortAnswerQuestion("Explain Observer.", "Observer",
                "The subject notifies observers.", "Mention notifications.");
        Quiz quiz = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, Arrays.asList(objective, shortAnswer));
        QuizRepository repository = new QuizRepository(directory);
        repository.saveQuiz(quiz);
        attempt = new QuizAttempt(quiz.getId(), course.getId());
        Map<String, String> answers = new HashMap<>();
        answers.put(objective.getId(), "true");
        answers.put(shortAnswer.getId(), "The subject notifies observers.");
        attempt.submit(answers);
        attempt.addResult(new QuestionResult(objective, GradingStatus.GRADED, 1, "Correct."));
        attempt.addResult(new QuestionResult(shortAnswer, GradingStatus.PENDING, 0, "Awaiting grading."));
        repository.saveAttempt(attempt);
    }

    @Test
    public void restoresCountsFromSavedRecordsAndExcludesPendingAnswers() {
        ProgressSummary summary = facade.getProgress(course.getId());
        assertEquals(0, summary.getReviewsDone());
        assertEquals(1, summary.getQuizzesTaken());
        assertEquals(1, summary.getGradedCount());
        assertEquals(1, summary.getCorrectCount());
        assertEquals(1, summary.getTasksDone());
        assertEquals(2, facade.getHistory(course.getId()).size());
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void healthyReadsDoNotRewriteFilesOrNotifyObservers() throws Exception {
        facade.getProgress(course.getId());
        Path progress = directory.resolve("progress.json");
        Path history = directory.resolve("history.json");
        String savedProgress = Files.readString(progress);
        String savedHistory = Files.readString(history);
        FileTime progressTime = Files.getLastModifiedTime(progress);
        FileTime historyTime = Files.getLastModifiedTime(history);
        final int[] notifications = {0};
        facade.observeProgress(new Observer() {
            public void update(Subject subject) {
                notifications[0]++;
            }
        });
        for (int i = 0; i < 3; i++) {
            facade.getProgress(course.getId());
            facade.getHistory(course.getId());
        }
        assertEquals(savedProgress, Files.readString(progress));
        assertEquals(savedHistory, Files.readString(history));
        assertEquals(progressTime, Files.getLastModifiedTime(progress));
        assertEquals(historyTime, Files.getLastModifiedTime(history));
        assertEquals(0, notifications[0]);
    }

    @Test
    public void restoresASavedReviewAfterItsProgressWriteFails() throws Exception {
        facade.getProgress(course.getId());
        Path progress = directory.resolve("progress.json");
        Files.delete(progress);
        Files.createDirectory(progress);
        try {
            facade.reviewCard(course.getId(), card.getId(), true);
            fail("A failed progress save must be reported.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("could not be saved"));
        }
        Course saved = new CourseRepository(directory).loadCourse(course.getId());
        assertEquals(1, saved.getFlashcard(card.getId()).getReviewCount());
        assertEquals(1, saved.getReviewEvents().size());
        Files.delete(progress);
        assertEquals(1, facade.getProgress(course.getId()).getReviewsDone());
        AgentFacade restarted = new AgentFacade(config, llm);
        assertEquals(1, restarted.getProgress(course.getId()).getReviewsDone());
        assertEquals(3, restarted.getHistory(course.getId()).size());
    }

    @Test
    public void restoresTheOriginalReviewEventAfterItsHistoryWriteFails() throws Exception {
        facade.getProgress(course.getId());
        Path history = directory.resolve("history.json");
        Files.delete(history);
        Files.createDirectory(history);
        try {
            facade.reviewCard(course.getId(), card.getId(), false);
            fail("A failed history save must be reported.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("could not be saved"));
        }
        StudyEvent saved = new CourseRepository(directory).loadCourse(course.getId()).getReviewEvents().get(0);
        Files.delete(history);
        StudyEvent restored = facade.getHistory(course.getId()).get(2);
        assertEquals(saved.getId(), restored.getId());
        assertEquals(saved.getTime(), restored.getTime());
        assertEquals(saved.getDescription(), restored.getDescription());
        assertEquals(1, facade.getProgress(course.getId()).getReviewsDone());
        assertEquals(3, facade.getHistory(course.getId()).size());
    }

    @Test
    public void backsUpDamagedFilesAndRebuildsCountsAcrossARestart() throws Exception {
        facade.reviewCard(course.getId(), card.getId(), true);
        facade.reviewCard(course.getId(), card.getId(), false);
        Path courseFile = directory.resolve("courses").resolve(course.getId() + ".json");
        Path attemptFile = directory.resolve("attempts").resolve(attempt.getId() + ".json");
        String savedCourse = Files.readString(courseFile);
        String savedAttempt = Files.readString(attemptFile);
        Files.writeString(directory.resolve("progress.json"), "{broken progress");
        Files.writeString(directory.resolve("history.json"), "{broken history");
        AgentFacade restarted = new AgentFacade(config, llm);
        ProgressSummary summary = restarted.getProgress(course.getId());
        assertEquals(2, summary.getReviewsDone());
        assertEquals(1, summary.getQuizzesTaken());
        assertEquals(1, summary.getGradedCount());
        assertEquals(1, summary.getTasksDone());
        assertEquals(4, restarted.getHistory(course.getId()).size());
        assertEquals(savedCourse, Files.readString(courseFile));
        assertEquals(savedAttempt, Files.readString(attemptFile));
        int backups = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.bak-*")) {
            for (Path file : files) {
                String original = "{broken history";
                if (file.getFileName().toString().startsWith("progress")) {
                    original = "{broken progress";
                }
                assertEquals(original, Files.readString(file));
                backups++;
            }
        }
        assertEquals(2, backups);
        assertTrue(restarted.getStorageWarnings().contains("backed up"));
    }

    @Test
    public void restoresLegacyReviewCountsWithoutInventingReviewEvents() throws Exception {
        course.setFlashcards(Arrays.asList(new Flashcard(card.getId(), card.getFront(), card.getBack(), "Observer", 3, 2)));
        new CourseRepository(directory).saveCourse(course);
        Path file = directory.resolve("courses").resolve(course.getId() + ".json");
        JSONObject old = new JSONObject(Files.readString(file));
        old.remove("reviewEvents");
        Files.writeString(file, old.toString());
        assertEquals(3, facade.getProgress(course.getId()).getReviewsDone());
        assertTrue(facade.getCourse(course.getId()).getReviewEvents().isEmpty());
        assertEquals(2, facade.getHistory(course.getId()).size());
    }

    @Test
    public void labelsRestoredQuizAndTaskTimesAsSnapshots() {
        facade.getProgress(course.getId());
        StudyEvent quizEvent = facade.getHistory(course.getId()).get(0);
        StudyEvent taskEvent = facade.getHistory(course.getId()).get(1);
        assertEquals(attempt.getSubmittedAt(), quizEvent.getTime());
        assertTrue(quizEvent.getDescription().contains("not a later grading time"));
        assertTrue(taskEvent.getDescription().contains("recovery time"));
        assertTrue(taskEvent.getDescription().contains("original completion time is unavailable"));
    }

    @Test
    public void missingSourceRecordsDoNotErasePreviouslySavedCounts() throws Exception {
        facade.getProgress(course.getId());
        String saved = Files.readString(directory.resolve("progress.json"));
        Files.delete(directory.resolve("attempts").resolve(attempt.getId() + ".json"));
        assertEquals(1, facade.getProgress(course.getId()).getQuizzesTaken());
        assertEquals(saved, Files.readString(directory.resolve("progress.json")));
        assertTrue(facade.getStorageWarnings().contains("some saved study records are missing"));
    }

    @Test
    public void damagedSourceRecordsArePreservedAndDoNotReplaceExistingCounts() throws Exception {
        facade.getProgress(course.getId());
        Path file = directory.resolve("plans").resolve(plan.getId() + ".json");
        Files.writeString(file, "{damaged plan");
        assertEquals(1, facade.getProgress(course.getId()).getTasksDone());
        assertEquals("{damaged plan", Files.readString(file));
        assertTrue(facade.getStorageWarnings().contains("recovery is incomplete"));
    }

    @Test
    public void recoveryAndHistoryRemainScopedToTheSelectedCourse() {
        Course other = facade.createCourse("Other course");
        facade.getProgress(course.getId());
        assertEquals(0, facade.getProgress(other.getId()).getGradedCount());
        assertEquals(0, facade.getProgress(other.getId()).getTasksDone());
        assertTrue(facade.getHistory(other.getId()).isEmpty());
    }

    @Test
    public void restoringANewlyGradedPendingAnswerDoesNotCountTheAttemptAgain() throws Exception {
        facade.getProgress(course.getId());
        QuestionResult pending = attempt.getResults().get(1);
        facade.selfGrade(course.getId(), attempt.getId(), pending.getQuestionId(), false);
        Files.delete(directory.resolve("progress.json"));
        AgentFacade restarted = new AgentFacade(config, llm);
        ProgressSummary summary = restarted.getProgress(course.getId());
        assertEquals(1, summary.getQuizzesTaken());
        assertEquals(2, summary.getGradedCount());
        assertEquals(1, summary.getCorrectCount());
        assertEquals(3, restarted.getHistory(course.getId()).size());
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void cliReportsWarningsDiscoveredDuringTheCommand() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CLI cli = new CLI(facade, new PrintStream(output), new PrintStream(errors));
        assertEquals(0, cli.runCommand(new String[] {"progress", "--course", course.getId()}));
        assertTrue(output.toString().contains("quizzes taken: 1"));
        assertTrue(errors.toString().contains("Progress was rebuilt"));
        assertTrue(errors.toString().contains("snapshots"));
    }
}
