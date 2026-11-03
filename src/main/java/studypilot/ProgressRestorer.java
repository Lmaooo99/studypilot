package studypilot;

import java.time.LocalDateTime;
import java.util.List;

public class ProgressRestorer {
    private final ProgressTracker tracker;
    private final HistoryRecorder history;
    private final ProgressRepository progress;
    private final QuizRepository quizzes;
    private final StudyPlanRepository plans;

    public ProgressRestorer(ProgressTracker tracker, HistoryRecorder history, ProgressRepository progress,
                            QuizRepository quizzes, StudyPlanRepository plans) {
        this.tracker = tracker;
        this.history = history;
        this.progress = progress;
        this.quizzes = quizzes;
        this.plans = plans;
    }

    public void restore(Course course) {
        List<QuizAttempt> attempts;
        List<StudyPlan> savedPlans;
        try {
            attempts = quizzes.listAttempts(course.getId());
            savedPlans = plans.listPlans(course.getId());
        } catch (IllegalStateException | IllegalArgumentException e) {
            progress.addWarning("Progress recovery is incomplete for course " + course.getId()
                    + ": saved attempts or plans could not be read. Existing counts and source files were kept.");
            return;
        }
        ProgressSummary rebuilt = rebuildSummary(course, attempts, savedPlans);
        ProgressSummary previous = tracker.getSummary(course.getId());
        if (!rebuilt.getAttemptIds().containsAll(previous.getAttemptIds())
                || !rebuilt.getCompletedTaskIds().containsAll(previous.getCompletedTaskIds())
                || rebuilt.getReviewsDone() < previous.getReviewsDone()) {
            progress.addWarning("Progress recovery is incomplete for course " + course.getId()
                    + ": some saved study records are missing. Existing counts were kept.");
            return;
        }
        if (tracker.restoreSummary(rebuilt)) {
            progress.addWarning("Progress was rebuilt for course " + course.getId() + " from saved study records.");
        }
        restoreHistory(course, attempts, savedPlans);
    }

    private ProgressSummary rebuildSummary(Course course, List<QuizAttempt> attempts, List<StudyPlan> savedPlans) {
        ProgressSummary summary = new ProgressSummary(course.getId(), course.getTotalReviews());
        for (QuizAttempt attempt : attempts) {
            if (attempt.isSubmitted()) {
                summary.recordQuizAttempt(attempt);
            }
        }
        for (StudyPlan plan : savedPlans) {
            for (StudyTask task : plan.getTasks()) {
                if (task.isDone()) {
                    summary.addTask(task.getId());
                }
            }
        }
        return summary;
    }

    private void restoreHistory(Course course, List<QuizAttempt> attempts, List<StudyPlan> savedPlans) {
        history.saveIfMissing();
        for (StudyEvent event : course.getReviewEvents()) {
            if (!history.contains(event.getId())) {
                tracker.recordEvent(event);
                progress.addWarning("Missing review history was restored from saved review events.");
            }
        }
        for (QuizAttempt attempt : attempts) {
            if (attempt.isSubmitted()) {
                restoreAttempt(attempt);
            }
        }
        for (StudyPlan plan : savedPlans) {
            for (StudyTask task : plan.getTasks()) {
                if (task.isDone()) {
                    restoreTask(course.getId(), task);
                }
            }
        }
    }

    private void restoreAttempt(QuizAttempt attempt) {
        int graded = attempt.getResults().size() - attempt.getPendingCount();
        String eventId = attempt.getId() + "-graded-" + graded;
        if (!history.contains(eventId)) {
            String description = "Restored quiz attempt snapshot " + attempt.getId()
                    + "; graded answers: " + graded + "; pending: " + attempt.getPendingCount()
                    + ". Timestamp is the original submission time, not a later grading time.";
            tracker.recordEvent(new StudyEvent(eventId, attempt.getSubmittedAt(), attempt.getCourseId(), "", description));
            progress.addWarning("Missing quiz history was restored as current attempt snapshots; earlier grading events are unavailable.");
        }
    }

    private void restoreTask(String courseId, StudyTask task) {
        String eventId = "task-" + task.getId();
        if (!history.contains(eventId)) {
            tracker.recordEvent(new StudyEvent(eventId, LocalDateTime.now(), courseId, task.getConceptName(),
                    "Restored completed task " + task.getId() + ": " + task.getDescription()
                    + ". Timestamp is the recovery time; the original completion time is unavailable."));
            progress.addWarning("Missing task history was restored as completed-task snapshots with recovery timestamps.");
        }
    }
}
