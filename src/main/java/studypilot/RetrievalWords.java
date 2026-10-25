package studypilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RetrievalWords {
    private RetrievalWords() { }

    static Map<String, Integer> countWords(String text) {
        Map<String, Integer> counts = new HashMap<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                int count = counts.getOrDefault(word, 0);
                counts.put(word, count + 1);
            }
        }
        return counts;
    }
    static List<Chunk> selectTopChunks(List<Chunk> chunks, double[] scores, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Retrieval count must be positive.");
        }
        List<Chunk> result = new ArrayList<>();
        boolean[] used = new boolean[chunks.size()];
        while (result.size() < k) {
            int best = -1;
            for (int i = 0; i < chunks.size(); i++) {
                if (used[i] || scores[i] <= 0) {
                    continue;
                }
                if (best == -1 || scores[i] > scores[best]) {
                    best = i;
                }
            }
            if (best == -1) {
                break;
            }
            used[best] = true;
            result.add(chunks.get(best));
        }
        return result;
    }
}
