package studypilot;

import java.util.ArrayList;
import java.util.List;

public class SummaryService {
    private final LLMClient llmClient;
    private final PromptTemplates templates;
    private final CourseRepository repository;

    public SummaryService(LLMClient llmClient, PromptTemplates templates, CourseRepository repository) {
        this.llmClient = llmClient;
        this.templates = templates;
        this.repository = repository;
    }

    public String summarize(Course course, Material material, String style) {
        if (!style.equals("brief") && !style.equals("detailed") && !style.equals("bullet")) {
            throw new IllegalArgumentException("Summary style must be brief, detailed or bullet.");
        }
        List<Chunk> chunks = material.getChunks();
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("This material has no chunks.");
        }
        if (chunks.size() > 400) {
            throw new IllegalArgumentException("The material exceeds 20 summary batches. Import a smaller section.");
        }
        List<String> summaries = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += 20) {
            List<Chunk> batch = new ArrayList<>();
            int end = Math.min(start + 20, chunks.size());
            for (int i = start; i < end; i++) {
                batch.add(chunks.get(i));
            }
            String prompt = templates.summaryPrompt(style, batch);
            summaries.add(generate(prompt));
        }
        String summary;
        if (summaries.size() == 1) {
            summary = summaries.get(0);
        } else {
            String prompt = templates.mergeSummaryPrompt(style, summaries);
            summary = generate(prompt);
        }
        material.setSummary(summary);
        repository.saveCourse(course);
        return summary;
    }

    private String generate(String prompt) {
        String errorMessage = "";
        for (int attempt = 0; attempt < 2; attempt++) {
            LLMResponse response = llmClient.generate(prompt);
            if (response.isSuccess() && !response.getText().isBlank()) {
                return response.getText();
            }
            errorMessage = response.getErrorMessage();
        }
        throw new IllegalStateException("Summary could not be generated: " + errorMessage);
    }
}
