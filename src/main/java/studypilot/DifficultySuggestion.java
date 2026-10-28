package studypilot;

public class DifficultySuggestion {
    private final Difficulty level;
    private final double accuracy;
    private final int gradedCount;
    private final String reason;

    public DifficultySuggestion(Difficulty level, double accuracy, int gradedCount, String reason) {
        this.level = level;
        this.accuracy = accuracy;
        this.gradedCount = gradedCount;
        this.reason = reason;
    }

    public Difficulty getLevel() {
        return level;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public int getGradedCount() {
        return gradedCount;
    }

    public String getReason() {
        return reason;
    }

    public String toString() {
        return "Suggested difficulty: " + level + "\n" + reason;
    }
}
