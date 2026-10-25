package studypilot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TfIdfRetrieval implements RetrievalStrategy {
    public List<Chunk> retrieve(String query, List<Chunk> chunks, int k) {
        Map<String, Integer> documentCounts = countDocumentsWithEachWord(chunks);
        Map<String, Integer> queryWords = RetrievalWords.countWords(query);
        int documentTotal = chunks.size();

        double queryLengthSquared = 0;
        for (String word : queryWords.keySet()) {
            double weight = wordWeight(word, queryWords, documentCounts, documentTotal);
            queryLengthSquared += weight * weight;
        }

        double[] scores = new double[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Integer> chunkWords = RetrievalWords.countWords(chunks.get(i).getText());
            double chunkLengthSquared = 0;
            double dotProduct = 0;
            for (String word : chunkWords.keySet()) {
                double chunkWeight = wordWeight(word, chunkWords, documentCounts, documentTotal);
                double queryWeight = wordWeight(word, queryWords, documentCounts, documentTotal);
                chunkLengthSquared += chunkWeight * chunkWeight;
                dotProduct += chunkWeight * queryWeight;
            }
            // Divide by both vector lengths so longer chunks do not win just for being longer.
            if (chunkLengthSquared > 0 && queryLengthSquared > 0) {
                double lengths = Math.sqrt(chunkLengthSquared * queryLengthSquared);
                scores[i] = dotProduct / lengths;
            }
        }
        return RetrievalWords.selectTopChunks(chunks, scores, k);
    }

    private Map<String, Integer> countDocumentsWithEachWord(List<Chunk> chunks) {
        Map<String, Integer> counts = new HashMap<>();
        for (Chunk chunk : chunks) {
            Map<String, Integer> words = RetrievalWords.countWords(chunk.getText());
            for (String word : words.keySet()) {
                counts.put(word, counts.getOrDefault(word, 0) + 1);
            }
        }
        return counts;
    }

    private double wordWeight(String word, Map<String, Integer> wordCounts,
                              Map<String, Integer> documentCounts, int documentTotal) {
        int occurrences = wordCounts.getOrDefault(word, 0);
        int documentsWithWord = documentCounts.getOrDefault(word, 0);
        // A word found in fewer chunks receives a larger weight.
        double inverseDocumentFrequency = Math.log((documentTotal + 1.0) / (documentsWithWord + 1.0)) + 1;
        return occurrences * inverseDocumentFrequency;
    }
}
