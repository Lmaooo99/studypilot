package studypilot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProgressTracker extends Subject {
    private final Map<String, ProgressSummary> summaries;
    private final ProgressRepository repository;
    private StudyEvent lastEvent;
    private boolean savePending;

    public ProgressTracker(ProgressRepository repository) {
        this.repository = repository;
        Map<String, ProgressSummary> saved;
        try {
            saved = repository.loadSummaries();
        } catch (IllegalStateException e) {
            saved = new HashMap<>();
            repository.addWarning("progress.json could not be read. Saved study records will be used; the blocked path was kept.");
        }
        summaries = saved;
    }

    public synchronized void recordReview(Course course, StudyEvent event) {
        if (!event.getCourseId().equals(course.getId()) || !course.getReviewEvents().contains(event)) {
            throw new IllegalArgumentException("The review event must be saved with this course.");
        }
        ProgressSummary summary = getSummary(course.getId());
        summary.setReviewsDone(course.getTotalReviews());
        saveSummary(summary);
        recordEvent(event);
    }

    public synchronized ProgressSummary getSummary(String courseId) {
        ProgressSummary summary = summaries.get(courseId);
        if (summary == null) {
            return new ProgressSummary(courseId, 0);
        }
        return summary.copy();
    }

    private boolean saveSummary(ProgressSummary summary) {
        if (!savePending && summary.hasSameCounts(getSummary(summary.getCourseId()))
                && (!summaries.containsKey(summary.getCourseId()) || repository.hasProgressFile())) {
            return false;
        }
        Map<String, ProgressSummary> changed = new HashMap<>(summaries);
        changed.put(summary.getCourseId(), summary.copy());
        saveSummaries(changed);
        summaries.put(summary.getCourseId(), changed.get(summary.getCourseId()));
        return true;
    }

    private void saveSummaries(Map<String, ProgressSummary> changed) {
        try {
            repository.saveSummaries(changed);
            savePending = false;
        } catch (IllegalStateException e) {
            savePending = true;
            repository.addWarning("Study records are saved, but progress.json could not be updated. "
                    + "Current counts are kept in memory; a later course read will retry the save.");
        }
    }

    public synchronized boolean restoreSummary(ProgressSummary summary) {
        boolean changed = !summary.hasSameCounts(getSummary(summary.getCourseId()));
        saveSummary(summary);
        if (changed) {
            lastEvent = null;
            notifyObservers();
        }
        return changed;
    }

    public synchronized void retryPendingSave() {
        if (savePending) {
            saveSummaries(new HashMap<>(summaries));
        }
    }

    public synchronized void recordEvent(StudyEvent event) {
        lastEvent = event;
        notifyObservers();
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

    public List<ConceptStats> getRankedStats(String courseId, int minResults) {
        if (minResults < 0) {
            throw new IllegalArgumentException("The minimum result count cannot be negative.");
        }
        List<ConceptStats> ranked = new ArrayList<>();
        for (ConceptStats stats : getSummary(courseId).getStats()) {
            if (stats.getGradedCount() < minResults) {
                continue;
            }
            int position = 0;
            for (ConceptStats current : ranked) {
                if (stats.getAccuracy() < current.getAccuracy()) {
                    break;
                }
                if (stats.getAccuracy() == current.getAccuracy()
                        && stats.getConceptName().compareToIgnoreCase(current.getConceptName()) < 0) {
                    break;
                }
                position++;
            }
            ranked.add(position, new ConceptStats(stats.getConceptName(), stats.getGradedCount(), stats.getCorrectCount()));
        }
        return ranked;
    }

    public synchronized void recordQuizAttempt(QuizAttempt attempt) {
        if (!attempt.isSubmitted()) {
            throw new IllegalArgumentException("Only a submitted attempt can update progress.");
        }
        String courseId = attempt.getCourseId();
        ProgressSummary summary = getSummary(courseId);
        summary.recordQuizAttempt(attempt);
        saveSummary(summary);
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
        recordEvent(new StudyEvent(eventId, LocalDateTime.now(), courseId, "", description));
    }

    public synchronized StudyEvent getLastEvent() {
        return lastEvent;
    }

    public synchronized void recordTaskDone(String courseId, StudyTask task) {
        if (!task.isDone()) {
            throw new IllegalArgumentException("Only a completed task can update progress.");
        }
        ProgressSummary summary = getSummary(courseId);
        summary.addTask(task.getId());
        saveSummary(summary);
        LocalDateTime time = task.getCompletedAt();
        String description = "Completed study task " + task.getId() + ": " + task.getDescription();
        if (time == null) {
            time = LocalDateTime.now();
            description += ". Timestamp is the recovery time; the original completion time is unavailable.";
        }
        recordEvent(new StudyEvent("task-" + task.getId(), time, courseId, task.getConceptName(), description));
    }

    public void addStorageWarning(String message) {
        repository.addWarning(message);
    }
}
