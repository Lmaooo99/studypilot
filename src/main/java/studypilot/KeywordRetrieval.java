package studypilot;

import java.util.List;
import java.util.Map;

public class KeywordRetrieval implements RetrievalStrategy {
    public List<Chunk> retrieve(String query, List<Chunk> chunks, int k) {
        Map<String, Integer> words = RetrievalWords.countWords(query);
        double[] scores = new double[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Integer> document = RetrievalWords.countWords(chunks.get(i).getText());
            for (String word : words.keySet()) {
                scores[i] += document.getOrDefault(word, 0);
            }
        }
        return RetrievalWords.selectTopChunks(chunks, scores, k);
    }
}
