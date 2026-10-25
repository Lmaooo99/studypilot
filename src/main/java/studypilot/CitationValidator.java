package studypilot;

import java.util.ArrayList;
import java.util.List;

public class CitationValidator {
    public List<String> validate(List<String> citedIds, List<Chunk> retrieved) {
        List<String> allowed = new ArrayList<>();
        for (Chunk chunk : retrieved) {
            allowed.add(chunk.getId());
        }
        List<String> valid = new ArrayList<>();
        for (String id : citedIds) {
            if (allowed.contains(id) && !valid.contains(id)) {
                valid.add(id);
            }
        }
        return valid;
    }
}
