package studypilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RetrievalWords {
    // Common words are ignored, so a question cannot match notes only through words like "the" or "what".
    // "s" and "t" are the pieces left over from words such as "what's" and "don't".
    private static final String[] STOP_WORDS = {
        "a", "about", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from",
        "how", "i", "in", "is", "it", "me", "my", "of", "on", "or", "s", "t", "that", "the", "this",
        "to", "was", "what", "when", "where", "which", "who", "why", "with", "you", "your"
    };

    private RetrievalWords() { }

    static Map<String, Integer> countWords(String text) {
        Map<String, Integer> counts = new HashMap<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty() && !isStopWord(word)) {
                int count = counts.getOrDefault(word, 0);
                counts.put(word, count + 1);
            }
        }
        return counts;
    }

    private static boolean isStopWord(String word) {
        for (String stopWord : STOP_WORDS) {
            if (stopWord.equals(word)) {
                return true;
            }
        }
        return false;
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
