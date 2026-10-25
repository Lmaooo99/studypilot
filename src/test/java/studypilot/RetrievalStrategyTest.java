package studypilot;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RetrievalStrategyTest {
    @Test
    public void bothStrategiesRankMatchingChunksAndExcludeUnrelatedChunks() {
        List<Chunk> chunks = new ArrayList<>();
        chunks.add(new Chunk("Observer observer subject updates", "a.txt", 0, 1, 1));
        chunks.add(new Chunk("Different unrelated content", "b.txt", 0, 1, 1));
        for (RetrievalStrategy strategy : new RetrievalStrategy[] {new KeywordRetrieval(), new TfIdfRetrieval()}) {
            List<Chunk> result = strategy.retrieve("OBSERVER", chunks, 5);
            assertEquals(1, result.size());
            assertEquals(chunks.get(0).getId(), result.get(0).getId());
            assertEquals(0, strategy.retrieve("nonmatching", chunks, 5).size());
        }
    }

    @Test
    public void tfIdfUsesCosineNormalizationForLongerChunks() {
        List<Chunk> chunks = new ArrayList<>();
        chunks.add(new Chunk("observer unrelated extra many words", "a.txt", 0, 1, 1));
        chunks.add(new Chunk("observer", "b.txt", 0, 1, 1));
        RetrievalStrategy strategy = new TfIdfRetrieval();
        List<Chunk> result = strategy.retrieve("observer", chunks, 1);
        assertEquals(chunks.get(1).getId(), result.get(0).getId());
    }
}
