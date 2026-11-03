package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressSaveTest {
    @Test
    public void aFailedSummarySaveDoesNotChangeTheTrackersCounts() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-summary-save-");
        Path blocked = directory.resolve("progress.json");
        ProgressRepository repository = new ProgressRepository(directory);
        ProgressTracker tracker = new ProgressTracker(repository);
        StudyTask task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read notes.", 20);
        task.markDone();
        Files.createDirectory(blocked);
        try {
            tracker.recordTaskDone("course", task);
            fail("A failed summary save must be reported.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("could not be saved"));
        }
        assertEquals(0, tracker.getSummary("course").getTasksDone());
        assertNull(tracker.getLastEvent());
        Files.delete(blocked);
        tracker.recordTaskDone("course", task);
        tracker.recordTaskDone("course", task);
        assertEquals(1, tracker.getSummary("course").getTasksDone());
        assertEquals(1, repository.loadSummaries().get("course").getTasksDone());
    }

    @Test
    public void aFailedHistorySaveDoesNotAddAnUnsavedEventToMemory() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-history-save-");
        Path blocked = directory.resolve("history.json");
        ProgressRepository repository = new ProgressRepository(directory);
        ProgressTracker tracker = new ProgressTracker(repository);
        HistoryRecorder history = new HistoryRecorder(tracker, repository);
        tracker.attach(history);
        StudyEvent event = new StudyEvent("course", "Observer", "Reviewed a flashcard.");
        Files.createDirectory(blocked);
        try {
            tracker.recordEvent(event);
            fail("A failed history save must be reported.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("could not be saved"));
        }
        assertTrue(history.getHistory("course").isEmpty());
        Files.delete(blocked);
        tracker.notifyObservers();
        tracker.notifyObservers();
        assertEquals(1, history.getHistory("course").size());
        assertEquals(1, repository.loadHistory().size());
    }

    @Test
    public void aReturnedSummaryCannotChangeStoredCounts() throws Exception {
        ProgressTracker tracker = new ProgressTracker(new ProgressRepository(Files.createTempDirectory("studypilot-summary-copy-")));
        StudyTask task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read notes.", 20);
        task.markDone();
        tracker.recordTaskDone("course", task);
        ProgressSummary copy = tracker.getSummary("course");
        copy.setReviewsDone(99);
        copy.addTask("another-task");
        assertEquals(0, tracker.getSummary("course").getReviewsDone());
        assertEquals(1, tracker.getSummary("course").getTasksDone());
    }

    @Test
    public void deletedSummaryAndHistoryFilesAreRecreatedFromSavedMemory() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-deleted-progress-");
        ProgressRepository repository = new ProgressRepository(directory);
        ProgressTracker tracker = new ProgressTracker(repository);
        HistoryRecorder history = new HistoryRecorder(tracker, repository);
        tracker.attach(history);
        StudyTask task = new StudyTask(LocalDate.now(), TaskType.READ, "Observer", "Read notes.", 20);
        task.markDone();
        tracker.recordTaskDone("course", task);
        Files.delete(directory.resolve("progress.json"));
        Files.delete(directory.resolve("history.json"));
        tracker.restoreSummary(tracker.getSummary("course"));
        history.saveIfMissing();
        assertEquals(1, repository.loadSummaries().get("course").getTasksDone());
        assertEquals(1, repository.loadHistory().size());
    }
}
