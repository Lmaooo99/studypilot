package studypilot;

public class ProgressSummary {
    private final String courseId;
    private int reviewsDone;

    public ProgressSummary(String courseId, int reviewsDone) {
        if (reviewsDone < 0) {
            throw new IllegalArgumentException("Invalid saved review count.");
        }
        this.courseId = courseId;
        this.reviewsDone = reviewsDone;
    }

    public String getCourseId() {
        return courseId;
    }

    public int getReviewsDone() {
        return reviewsDone;
    }

    public void addReview() {
        reviewsDone++;
    }

    public String toString() {
        return "Reviews completed: " + reviewsDone;
    }
}
