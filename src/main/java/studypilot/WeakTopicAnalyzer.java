package studypilot;

import java.util.ArrayList;
import java.util.List;

public class WeakTopicAnalyzer {
    private final ProgressTracker tracker;
    private final LLMClient llmClient;
    private final PromptTemplates templates;
    private final int minResults;

    public WeakTopicAnalyzer(ProgressTracker tracker, LLMClient llmClient, PromptTemplates templates, int minResults) {
        if (minResults < 1) {
            throw new IllegalArgumentException("The minimum graded result count must be positive.");
        }
        this.tracker = tracker;
        this.llmClient = llmClient;
        this.templates = templates;
        this.minResults = minResults;
    }

    public WeakTopicReport analyze(String courseId) {
        List<ConceptStats> ranked = tracker.getRankedStats(courseId, minResults);
        List<String> insufficient = new ArrayList<>();
        for (ConceptStats stats : tracker.getRankedStats(courseId, 0)) {
            if (stats.getGradedCount() < minResults) {
                insufficient.add(stats.getConceptName());
            }
        }
        if (ranked.isEmpty()) {
            return new WeakTopicReport(ranked, insufficient, "Not enough graded data. Each topic needs at least "
                    + minResults + " graded answers; no weakest topic is identified. No model call was made.");
        }
        LLMResponse response = llmClient.generate(templates.weakTopicPrompt(describeStats(ranked)));
        String explanation = "AI explanation unavailable. The graded-result ranking is still shown.";
        if (response.isSuccess() && !response.getText().isBlank()) {
            explanation = "AI explanation:\n" + response.getText().trim();
        }
        return new WeakTopicReport(ranked, insufficient, explanation);
    }

    private String describeStats(List<ConceptStats> ranked) {
        StringBuilder text = new StringBuilder();
        int rank = 1;
        for (ConceptStats stats : ranked) {
            text.append(rank + ". " + stats.getConceptName() + ": " + stats.getCorrectCount() + "/"
                    + stats.getGradedCount() + " correct; accuracy=" + stats.getAccuracy() + "\n");
            rank++;
        }
        return text.toString();
    }
}
