package studypilot;

import java.util.ArrayList;
import java.util.List;

public class WeakTopicReport {
    private final List<ConceptStats> rankedTopics;
    private final List<String> insufficientConcepts;
    private final String explanation;

    public WeakTopicReport(List<ConceptStats> rankedTopics, List<String> insufficientConcepts, String explanation) {
        this.rankedTopics = new ArrayList<>(rankedTopics);
        this.insufficientConcepts = new ArrayList<>(insufficientConcepts);
        this.explanation = explanation;
    }

    public List<ConceptStats> getRankedTopics() {
        return new ArrayList<>(rankedTopics);
    }

    public List<String> getInsufficientConcepts() {
        return new ArrayList<>(insufficientConcepts);
    }

    public String getExplanation() {
        return explanation;
    }

    public String toString() {
        StringBuilder text = new StringBuilder("Graded topics, lowest accuracy first:\n");
        if (rankedTopics.isEmpty()) {
            text.append("No topics have enough graded answers to rank.\n");
        }
        int rank = 1;
        for (ConceptStats stats : rankedTopics) {
            text.append(rank + ". " + stats.getConceptName() + " - " + Math.round(stats.getAccuracy() * 100)
                    + "% (" + stats.getCorrectCount() + "/" + stats.getGradedCount() + " correct)\n");
            rank++;
        }
        if (!insufficientConcepts.isEmpty()) {
            text.append("\nInsufficient data (not ranked):\n");
            for (String name : insufficientConcepts) {
                text.append(name + "\n");
            }
        }
        return text + "\n" + explanation;
    }
}
