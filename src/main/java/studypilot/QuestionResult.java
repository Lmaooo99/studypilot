package studypilot;

public class QuestionResult {
    private final String questionId;
    private final String conceptName;
    private final GradingStatus gradingStatus;
    private final boolean correct;
    private final int pointsEarned;
    private final int maxPoints;
    private final String explanation;
    private boolean counted;

    public QuestionResult(Question question, GradingStatus status, int pointsEarned, String explanation) {
        this(question, status, pointsEarned, explanation, false);
    }

    public QuestionResult(Question question, GradingStatus status, int pointsEarned, String explanation, boolean counted) {
        if (status == null || pointsEarned < 0 || pointsEarned > question.getPoints() || explanation.isBlank()) {
            throw new IllegalArgumentException("Invalid grading result.");
        }
        if (status == GradingStatus.PENDING && (pointsEarned != 0 || counted)) {
            throw new IllegalArgumentException("Pending answers cannot earn points or enter graded statistics.");
        }
        questionId = question.getId();
        conceptName = question.getConceptName();
        gradingStatus = status;
        correct = status == GradingStatus.GRADED && pointsEarned == question.getPoints();
        this.pointsEarned = pointsEarned;
        maxPoints = question.getPoints();
        this.explanation = explanation;
        this.counted = counted;
    }

    public String getQuestionId() {
        return questionId;
    }

    public String getConceptName() {
        return conceptName;
    }

    public GradingStatus getStatus() {
        return gradingStatus;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public String getExplanation() {
        return explanation;
    }

    public boolean isCounted() {
        return counted;
    }

    public void markCounted() {
        if (gradingStatus != GradingStatus.GRADED) {
            throw new IllegalStateException("A pending answer cannot be counted.");
        }
        counted = true;
    }
}
