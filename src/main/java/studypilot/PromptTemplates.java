package studypilot;

import java.util.List;

public class PromptTemplates {
    public String summaryPrompt(String style, List<Chunk> chunks) {
        return "Summarize the following course material in the requested style: " + style
                + ". Use only the supplied material. Return the summary text.\n" + context(chunks);
    }

    public String mergeSummaryPrompt(String style, List<String> summaries) {
        StringBuilder prompt = new StringBuilder("Combine these partial summaries into one " + style
                + " summary. Do not add facts.\n");
        for (String summary : summaries) {
            prompt.append("\nPARTIAL SUMMARY\n");
            prompt.append(summary);
        }
        return prompt.toString();
    }

    private String context(List<Chunk> chunks) {
        StringBuilder text = new StringBuilder("MATERIAL DATA BEGIN\n");
        for (Chunk chunk : chunks) {
            text.append("\n[" + chunk.getId() + "] " + chunk.getLocation() + "\n");
            text.append(chunk.getText() + "\n");
        }
        text.append("MATERIAL DATA END\n");
        return text.toString();
    }
}
