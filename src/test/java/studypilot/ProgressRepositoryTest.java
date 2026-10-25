package studypilot;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressRepositoryTest {
    @Test
    public void backsUpCorruptProgressAndHistoryBeforeStartingEmpty() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-progress-test-");
        Files.writeString(directory.resolve("progress.json"), "{broken progress");
        Files.writeString(directory.resolve("history.json"), "{broken history");
        ProgressRepository repository = new ProgressRepository(directory);
        assertEquals(0, repository.loadSummaries().size());
        assertEquals(0, repository.loadHistory().size());
        assertTrue(repository.getRecoveryWarning().contains("backed up"));
        int count = 0;
        try (DirectoryStream<Path> backups = Files.newDirectoryStream(directory, "*.bak-*")) {
            for (Path file : backups) {
                assertTrue(Files.readString(file).startsWith("{broken"));
                count++;
            }
        }
        assertEquals(2, count);
    }

    @Test
    public void observerRegistrationAndDuplicateNotificationDoNotDuplicateHistory() throws Exception {
        ProgressRepository repository = new ProgressRepository(Files.createTempDirectory("studypilot-observer-test-"));
        ProgressTracker tracker = new ProgressTracker(repository);
        HistoryRecorder history = new HistoryRecorder(tracker, repository);
        tracker.attach(history);
        tracker.attach(history);
        tracker.recordReview("test-course", new Flashcard("Question", "Answer", "Topic"), true);
        tracker.notifyObservers();
        assertEquals(1, history.getHistory("test-course").size());
        assertEquals(1, repository.loadHistory().size());
    }
}
