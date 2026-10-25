package studypilot;

import java.util.ArrayList;
import java.util.List;

public class Answer {
    private final String text;
    private final List<Citation> citations;
    public Answer(String text, List<Citation> citations) {
        this.text = text;
        this.citations = new ArrayList<>(citations);
    }

    public String getText() {
        return text;
    }

    public List<Citation> getCitations() {
        return new ArrayList<>(citations);
    }

    public boolean hasValidCitation() {
        return !citations.isEmpty();
    }

    public String toString() {
        StringBuilder display = new StringBuilder(text);
        if (hasValidCitation()) {
            display.append("\n\nSources (retrieved chunk IDs checked):");
            for (Citation citation : citations) {
                display.append("\n").append(citation);
            }
        }
        return display.toString();
    }
}
