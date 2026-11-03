package studypilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProgressSummary {
    private final String courseId;
    private int reviewsDone;
    private final List<String> attemptIds;
    private final List<String> countedResultIds;
    private final List<String> completedTaskIds;
    private final Map<String, ConceptStats> stats = new HashMap<>();

    public ProgressSummary(String courseId, int reviewsDone) {
        this(courseId, reviewsDone, new ArrayList<String>(), new ArrayList<String>(), new ArrayList<ConceptStats>());
    }

    public ProgressSummary(String courseId, int reviewsDone, List<String> attemptIds,
                           List<String> countedResultIds, List<ConceptStats> savedStats) {
        this(courseId, reviewsDone, attemptIds, countedResultIds, savedStats, new ArrayList<String>());
    }

    public ProgressSummary(String courseId, int reviewsDone, List<String> attemptIds,
                           List<String> countedResultIds, List<ConceptStats> savedStats,
                           List<String> completedTaskIds) {
        if (courseId == null || courseId.isBlank() || reviewsDone < 0) {
            throw new IllegalArgumentException("Invalid saved course or review count.");
        }
        this.courseId = courseId;
        this.reviewsDone = reviewsDone;
        this.attemptIds = copyIds(attemptIds);
        this.countedResultIds = copyIds(countedResultIds);
        this.completedTaskIds = copyIds(completedTaskIds);
        for (ConceptStats value : savedStats) {
            String key = value.getConceptName().toLowerCase(Locale.ROOT);
            if (stats.containsKey(key)) {
                throw new IllegalArgumentException("Repeated saved topic statistics.");
            }
            stats.put(key, new ConceptStats(value.getConceptName(), value.getGradedCount(), value.getCorrectCount()));
        }
        if (getGradedCount() != countedResultIds.size()) {
            throw new IllegalArgumentException("Saved graded counts do not match their result IDs.");
        }
    }

    private List<String> copyIds(List<String> ids) {
        List<String> copy = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank() || copy.contains(id)) {
                throw new IllegalArgumentException("Invalid saved progress IDs.");
            }
            copy.add(id);
        }
        return copy;
    }

    public String getCourseId() {
        return courseId;
    }

    public int getReviewsDone() {
        return reviewsDone;
    }

    public void setReviewsDone(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("The review count cannot be negative.");
        }
        reviewsDone = count;
    }

    public ProgressSummary copy() {
        return new ProgressSummary(courseId, reviewsDone, attemptIds, countedResultIds, getStats(), completedTaskIds);
    }

    public boolean hasSameCounts(ProgressSummary other) {
        if (!courseId.equals(other.courseId) || reviewsDone != other.reviewsDone
                || !sameIds(attemptIds, other.attemptIds) || !sameIds(countedResultIds, other.countedResultIds)
                || !sameIds(completedTaskIds, other.completedTaskIds) || stats.size() != other.stats.size()) {
            return false;
        }
        for (String key : stats.keySet()) {
            ConceptStats first = stats.get(key);
            ConceptStats second = other.stats.get(key);
            if (second == null || first.getGradedCount() != second.getGradedCount()
                    || first.getCorrectCount() != second.getCorrectCount()) {
                return false;
            }
        }
        return true;
    }

    private boolean sameIds(List<String> first, List<String> second) {
        return first.size() == second.size() && first.containsAll(second);
    }

    public int getQuizzesTaken() {
        return attemptIds.size();
    }

    public List<String> getAttemptIds() {
        return new ArrayList<>(attemptIds);
    }

    public List<String> getCountedResultIds() {
        return new ArrayList<>(countedResultIds);
    }

    public int getTasksDone() {
        return completedTaskIds.size();
    }

    public List<String> getCompletedTaskIds() {
        return new ArrayList<>(completedTaskIds);
    }

    public void addTask(String taskId) {
        if (!completedTaskIds.contains(taskId)) {
            completedTaskIds.add(taskId);
        }
    }

    public List<ConceptStats> getStats() {
        List<ConceptStats> values = new ArrayList<>();
        for (ConceptStats value : stats.values()) {
            values.add(new ConceptStats(value.getConceptName(), value.getGradedCount(), value.getCorrectCount()));
        }
        return values;
    }

    public int getGradedCount() {
        int count = 0;
        for (ConceptStats value : stats.values()) {
            if (value.getGradedCount() > Integer.MAX_VALUE - count) {
                throw new IllegalArgumentException("The saved graded count is too large.");
            }
            count += value.getGradedCount();
        }
        return count;
    }

    public int getCorrectCount() {
        int count = 0;
        for (ConceptStats value : stats.values()) {
            count += value.getCorrectCount();
        }
        return count;
    }

    public void recordQuizAttempt(QuizAttempt attempt) {
        if (!attemptIds.contains(attempt.getId())) {
            attemptIds.add(attempt.getId());
        }
        for (QuestionResult result : attempt.getResults()) {
            String resultId = attempt.getId() + ":" + result.getQuestionId();
            if (result.getStatus() != GradingStatus.GRADED || countedResultIds.contains(resultId)) {
                continue;
            }
            String key = result.getConceptName().toLowerCase(Locale.ROOT);
            ConceptStats value = stats.get(key);
            if (value == null) {
                value = new ConceptStats(result.getConceptName(), 0, 0);
                stats.put(key, value);
            }
            value.addResult(result.isCorrect());
            countedResultIds.add(resultId);
        }
    }

    public String toString() {
        return "Reviews completed: " + reviewsDone + "; quizzes taken: " + getQuizzesTaken()
                + "; graded answers: " + getGradedCount() + "; correct answers: " + getCorrectCount()
                + "; tasks completed: " + getTasksDone();
    }
}
