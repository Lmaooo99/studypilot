package studypilot;

public class ConceptStats {
    private final String conceptName;
    private int gradedCount;
    private int correctCount;

    public ConceptStats(String conceptName, int gradedCount, int correctCount) {
        if (conceptName.isBlank() || gradedCount < 0 || correctCount < 0 || correctCount > gradedCount) {
            throw new IllegalArgumentException("Invalid graded topic statistics.");
        }
        this.conceptName = conceptName.trim();
        this.gradedCount = gradedCount;
        this.correctCount = correctCount;
    }

    public String getConceptName() {
        return conceptName;
    }

    public int getGradedCount() {
        return gradedCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public double getAccuracy() {
        if (gradedCount == 0) {
            return 0;
        }
        return (double) correctCount / gradedCount;
    }

    public void addResult(boolean correct) {
        gradedCount++;
        if (correct) {
            correctCount++;
        }
    }
}
