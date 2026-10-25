package studypilot;

import java.util.ArrayList;
import java.util.List;

public class CitedAnswer {
    private final String answer;
    private final List<String> citedChunkIds;
    private final boolean needsClarification;
    public CitedAnswer(String answer, List<String> citedChunkIds, boolean needsClarification) {
        this.answer = answer;
        this.citedChunkIds = new ArrayList<>(citedChunkIds);
        this.needsClarification = needsClarification;
    }

    public String getAnswer() {
        return answer;
    }

    public List<String> getCitedChunkIds() {
        return new ArrayList<>(citedChunkIds);
    }

    public boolean needsClarification() {
        return needsClarification;
    }
}
