package studypilot;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;

public class QuestionAnswerService {
    private final RetrievalStrategy strategy;
    private final LLMClient llmClient;
    private final PromptTemplates templates;
    private final ResponseParser parser;
    private final CitationValidator validator;

    public QuestionAnswerService(RetrievalStrategy strategy, LLMClient llmClient, PromptTemplates templates,
                                 ResponseParser parser, CitationValidator validator) {
        this.strategy = strategy;
        this.llmClient = llmClient;
        this.templates = templates;
        this.parser = parser;
        this.validator = validator;
    }

    public Answer answer(Course course, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Enter a question.");
        }
        List<Chunk> retrieved = strategy.retrieve(question, course.getChunks(), 5);
        if (retrieved.isEmpty()) {
            return new Answer("No relevant chunks were found. Please name a course topic or import relevant material.", new ArrayList<Citation>());
        }
        String prompt = templates.qaPrompt(question, retrieved);
        for (int attempt = 0; attempt < 2; attempt++) {
            LLMResponse response = llmClient.generate(prompt);
            if (response.isSuccess()) {
                try {
                    CitedAnswer proposed = parser.parseCitedAnswer(response.getText());
                    if (proposed.needsClarification()) {
                        return new Answer("Clarification needed: " + proposed.getAnswer(), new ArrayList<Citation>());
                    }
                    List<String> validIds = validator.validate(proposed.getCitedChunkIds(), retrieved);
                    if (!validIds.isEmpty()) {
                        List<Citation> citations = createCitations(validIds, retrieved);
                        return new Answer(proposed.getAnswer(), citations);
                    }
                } catch (JSONException e) {
                    // Retry the model response; raw invalid output is never executed.
                }
            }
            prompt = templates.qaPrompt(question, retrieved) + "\nReturn only the requested JSON and use the supplied chunk IDs.";
        }
        throw new IllegalStateException("An answer with valid citations could not be generated after one retry.");
    }

    private List<Citation> createCitations(List<String> validIds, List<Chunk> retrieved) {
        List<Citation> citations = new ArrayList<>();
        for (String id : validIds) {
            for (Chunk chunk : retrieved) {
                if (chunk.getId().equals(id)) {
                    citations.add(new Citation(chunk));
                }
            }
        }
        return citations;
    }
}
