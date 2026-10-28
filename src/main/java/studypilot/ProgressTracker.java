package studypilot;

import java.time.LocalDateTime;
import java.util.Map;

public class ProgressTracker extends Subject {
    private final Map<String, ProgressSummary> summaries;
    private final ProgressRepository repository;
    private StudyEvent lastEvent;

    public ProgressTracker(ProgressRepository repository) {
        this.repository = repository;
        summaries = repository.loadSummaries();
    }

    public void recordReview(String courseId, Flashcard card, boolean remembered) {
        ProgressSummary summary = summaries.get(courseId);
        if (summary == null) {
            summary = new ProgressSummary(courseId, 0);
            summaries.put(courseId, summary);
        }
        summary.addReview();
        repository.saveSummaries(summaries);

        String description = "Reviewed flashcard " + card.getId();
        if (remembered) {
            description += ": remembered";
        } else {
            description += ": not yet";
        }
        lastEvent = new StudyEvent(courseId, card.getConceptName(), description);
        notifyObservers();
    }

    public ProgressSummary getSummary(String courseId) {
        ProgressSummary summary = summaries.get(courseId);
        if (summary == null) {
            return new ProgressSummary(courseId, 0);
        }
        return summary;
    }

    private ConceptStats findStats(String courseId, String topic) {
        for (ConceptStats stats : getSummary(courseId).getStats()) {
            if (stats.getConceptName().equalsIgnoreCase(topic.trim())) {
                return stats;
            }
        }
        return null;
    }

    public double getAccuracy(String courseId, String topic) {
        ConceptStats stats = findStats(courseId, topic);
        if (stats == null) {
            return 0;
        }
        return stats.getAccuracy();
    }

    public int getGradedCount(String courseId, String topic) {
        ConceptStats stats = findStats(courseId, topic);
        if (stats == null) {
            return 0;
        }
        return stats.getGradedCount();
    }

    public void recordQuizAttempt(QuizAttempt attempt) {
        if (!attempt.isSubmitted()) {
            throw new IllegalArgumentException("Only a submitted attempt can update progress.");
        }
        String courseId = attempt.getCourseId();
        ProgressSummary summary = summaries.get(courseId);
        if (summary == null) {
            summary = new ProgressSummary(courseId, 0);
            summaries.put(courseId, summary);
        }
        summary.recordQuizAttempt(attempt);
        repository.saveSummaries(summaries);
        for (QuestionResult result : attempt.getResults()) {
            if (result.getStatus() == GradingStatus.GRADED) {
                result.markCounted();
            }
        }
        int graded = attempt.getResults().size() - attempt.getPendingCount();
        // Repeated submissions use the same event ID, so history stays idempotent.
        String eventId = attempt.getId() + "-graded-" + graded;
        String description = "Quiz attempt " + attempt.getId() + "; graded answers: " + graded
                + "; pending: " + attempt.getPendingCount();
        lastEvent = new StudyEvent(eventId, LocalDateTime.now(), courseId, "", description);
        notifyObservers();
    }

    public StudyEvent getLastEvent() {
        return lastEvent;
    }
}
