package studypilot;

import java.util.List;

public interface RetrievalStrategy {
    List<Chunk> retrieve(String query, List<Chunk> chunks, int k);
}
