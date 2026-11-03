package studypilot;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
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
        Course course = new Course("Test course");
        Flashcard card = new Flashcard("Question", "Answer", "Topic");
        card.recordReview(true);
        course.setFlashcards(java.util.Arrays.asList(card));
        StudyEvent event = new StudyEvent(course.getId(), "Topic", "Reviewed flashcard.");
        course.addReviewEvent(event);
        tracker.recordReview(course, event);
        tracker.notifyObservers();
        assertEquals(1, history.getHistory(course.getId()).size());
        assertEquals(1, repository.loadHistory().size());
    }

    @Test
    public void loadsReviewOnlyProgressWithoutInventingQuizCounts() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-old-progress-test-");
        Files.writeString(directory.resolve("progress.json"), "{\"summaries\":[{\"courseId\":\"course\",\"reviewsDone\":3}]}");
        ProgressSummary summary = new ProgressRepository(directory).loadSummaries().get("course");
        assertEquals(3, summary.getReviewsDone());
        assertEquals(0, summary.getQuizzesTaken());
        assertEquals(0, summary.getGradedCount());
        assertEquals(0, summary.getTasksDone());
    }

    @Test
    public void backsUpHistoryWithAnInvalidTimestamp() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-history-time-test-");
        String damaged = "{\"events\":[{\"eventId\":\"event\",\"time\":\"invalid\",\"courseId\":\"course\","
                + "\"conceptName\":\"Topic\",\"description\":\"Review\"}]}";
        Files.writeString(directory.resolve("history.json"), damaged);
        ProgressRepository repository = new ProgressRepository(directory);
        assertEquals(0, repository.loadHistory().size());
        assertTrue(repository.getRecoveryWarning().contains("backed up"));
    }

    @Test
    public void rejectsFractionalStringAndBooleanProgressCounts() throws Exception {
        String[] values = {"1.5", "\"3\"", "true"};
        for (String value : values) {
            Path directory = Files.createTempDirectory("studypilot-progress-number-");
            String damaged = "{\"summaries\":[{\"courseId\":\"course\",\"reviewsDone\":" + value + "}]}";
            Files.writeString(directory.resolve("progress.json"), damaged);
            ProgressRepository repository = new ProgressRepository(directory);
            assertTrue(repository.loadSummaries().isEmpty());
            assertTrue(repository.getRecoveryWarning().contains("backed up"));
        }
    }

    @Test
    public void rejectsRepeatedCourseIdsInsteadOfOverwritingTheFirstSummary() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-repeated-course-progress-");
        JSONObject item = new JSONObject().put("courseId", "course").put("reviewsDone", 3);
        Files.writeString(directory.resolve("progress.json"),
                new JSONObject().put("summaries", new JSONArray().put(item).put(item)).toString());
        ProgressRepository repository = new ProgressRepository(directory);
        assertTrue(repository.loadSummaries().isEmpty());
        assertTrue(repository.getRecoveryWarning().contains("backed up"));
    }

    @Test
    public void rejectsRepeatedHistoryEventIds() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-repeated-history-");
        JSONObject item = new JSONObject().put("eventId", "event").put("time", "2026-11-03T07:00:00")
                .put("courseId", "course").put("conceptName", "Observer").put("description", "Reviewed a card.");
        Files.writeString(directory.resolve("history.json"),
                new JSONObject().put("events", new JSONArray().put(item).put(item)).toString());
        ProgressRepository repository = new ProgressRepository(directory);
        assertTrue(repository.loadHistory().isEmpty());
        assertTrue(repository.getRecoveryWarning().contains("backed up"));
    }

    @Test
    public void preservesDirectoriesAtProgressAndHistoryPaths() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-progress-directory-");
        Files.createDirectory(directory.resolve("progress.json"));
        Files.createDirectory(directory.resolve("history.json"));
        ProgressRepository repository = new ProgressRepository(directory);
        try {
            repository.loadSummaries();
            fail("A directory must not be moved into a JSON backup.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("not a regular file"));
        }
        try {
            repository.loadHistory();
            fail("A directory must not be moved into a JSON backup.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("not a regular file"));
        }
        assertTrue(Files.isDirectory(directory.resolve("progress.json")));
        assertTrue(Files.isDirectory(directory.resolve("history.json")));
    }

    @Test
    public void rejectsBlankHistoryDescriptions() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-empty-history-event-");
        JSONObject item = new JSONObject().put("eventId", "event").put("time", "2026-11-03T07:00:00")
                .put("courseId", "course").put("conceptName", "").put("description", " ");
        Files.writeString(directory.resolve("history.json"),
                new JSONObject().put("events", new JSONArray().put(item)).toString());
        ProgressRepository repository = new ProgressRepository(directory);
        assertTrue(repository.loadHistory().isEmpty());
        assertTrue(repository.getRecoveryWarning().contains("backed up"));
    }
}
