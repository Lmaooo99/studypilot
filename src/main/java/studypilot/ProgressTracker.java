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

    public ProgressTracker(ProgressRepository repository) {
        this.repository = repository;
        summaries = repository.loadSummaries();
    }

    public void recordReview(Course course, StudyEvent event) {
        if (!event.getCourseId().equals(course.getId()) || !course.getReviewEvents().contains(event)) {
            throw new IllegalArgumentException("The review event must be saved with this course.");
        }
        ProgressSummary summary = getSummary(course.getId());
        summary.setReviewsDone(course.getTotalReviews());
        saveSummary(summary);
        recordEvent(event);
    }

    public ProgressSummary getSummary(String courseId) {
        ProgressSummary summary = summaries.get(courseId);
        if (summary == null) {
            return new ProgressSummary(courseId, 0);
        }
        return summary.copy();
    }

    private boolean saveSummary(ProgressSummary summary) {
        if (summary.hasSameCounts(getSummary(summary.getCourseId()))
                && (!summaries.containsKey(summary.getCourseId()) || repository.hasProgressFile())) {
            return false;
        }
        Map<String, ProgressSummary> changed = new HashMap<>(summaries);
        changed.put(summary.getCourseId(), summary.copy());
        repository.saveSummaries(changed);
        summaries.put(summary.getCourseId(), changed.get(summary.getCourseId()));
        return true;
    }

    public boolean restoreSummary(ProgressSummary summary) {
        boolean changed = saveSummary(summary);
        if (changed) {
            lastEvent = null;
            notifyObservers();
        }
        return changed;
    }

    public void recordEvent(StudyEvent event) {
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

    public void recordQuizAttempt(QuizAttempt attempt) {
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

    public StudyEvent getLastEvent() {
        return lastEvent;
    }

    public void recordTaskDone(String courseId, StudyTask task) {
        if (!task.isDone()) {
            throw new IllegalArgumentException("Only a completed task can update progress.");
        }
        ProgressSummary summary = getSummary(courseId);
        summary.addTask(task.getId());
        saveSummary(summary);
        recordEvent(new StudyEvent("task-" + task.getId(), LocalDateTime.now(), courseId,
                task.getConceptName(), "Completed study task " + task.getId() + ": " + task.getDescription()));
    }
}
