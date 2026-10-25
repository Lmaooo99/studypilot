package studypilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;

public class ConceptService {
    private final LLMClient llmClient;
    private final PromptTemplates templates;
    private final ResponseParser parser;
    private final CourseRepository repository;

    public ConceptService(LLMClient llmClient, PromptTemplates templates, ResponseParser parser, CourseRepository repository) {
        this.llmClient = llmClient;
        this.templates = templates;
        this.parser = parser;
        this.repository = repository;
    }

    public List<Concept> extractConcepts(Course course, Material material) {
        List<Chunk> chunks = material.getChunks();
        if (chunks.isEmpty() || chunks.size() > 400) {
            throw new IllegalArgumentException("Select a material with 1 to 400 chunks for concept extraction.");
        }
        List<Concept> proposed = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += 20) {
            List<Chunk> batch = new ArrayList<>();
            int end = Math.min(start + 20, chunks.size());
            for (int i = start; i < end; i++) {
                batch.add(chunks.get(i));
            }
            proposed.addAll(extractBatch(batch));
        }
        if (proposed.isEmpty()) {
            return new ArrayList<>();
        }
        List<Concept> merged = course.getConcepts();
        mergeConcepts(merged, proposed);
        removeInvalidParents(merged);
        course.setConcepts(merged);
        repository.saveCourse(course);
        return merged;
    }

    private void mergeConcepts(List<Concept> merged, List<Concept> proposed) {
        for (Concept candidate : proposed) {
            Concept existing = find(merged, candidate.getName());
            if (existing == null) {
                merged.add(candidate);
            } else {
                for (String id : candidate.getSourceChunkIds()) {
                    existing.addSource(id);
                }
                if (existing.getParentName().isEmpty()) {
                    existing.setParentName(candidate.getParentName());
                }
            }
        }
    }

    private List<Concept> extractBatch(List<Chunk> chunks) {
        String prompt = templates.conceptPrompt(chunks);
        for (int attempt = 0; attempt < 2; attempt++) {
            LLMResponse response = llmClient.generate(prompt);
            if (response.isSuccess()) {
                try {
                    List<Concept> concepts = parser.parseConcepts(response.getText());
                    checkSources(concepts, chunks);
                    return concepts;
                } catch (JSONException e) {
                    prompt = templates.conceptPrompt(chunks) + "\nReturn only valid JSON with the required fields and supplied chunk IDs.";
                }
            }
        }
        throw new IllegalStateException("Concept extraction failed after one retry. No concepts were saved.");
    }

    private void checkSources(List<Concept> concepts, List<Chunk> chunks) {
        List<String> allowedIds = new ArrayList<>();
        for (Chunk chunk : chunks) {
            allowedIds.add(chunk.getId());
        }
        for (Concept concept : concepts) {
            for (String id : concept.getSourceChunkIds()) {
                if (!allowedIds.contains(id)) {
                    throw new JSONException("Unknown concept source");
                }
            }
        }
    }

    private Concept find(List<Concept> concepts, String name) {
        for (Concept concept : concepts) {
            if (concept.getName().equalsIgnoreCase(name.trim())) {
                return concept;
            }
        }
        return null;
    }

    private void removeInvalidParents(List<Concept> concepts) {
        Map<String, Concept> conceptsByName = new HashMap<>();
        for (Concept concept : concepts) {
            conceptsByName.put(concept.getName().toLowerCase(Locale.ROOT), concept);
        }
        for (Concept concept : concepts) {
            List<String> visitedNames = new ArrayList<>();
            Concept current = concept;
            while (current != null && !current.getParentName().isEmpty()) {
                String name = current.getName().toLowerCase(Locale.ROOT);
                if (visitedNames.contains(name)) {
                    concept.setParentName("");
                    break;
                }
                visitedNames.add(name);
                String parentName = current.getParentName().toLowerCase(Locale.ROOT);
                Concept parent = conceptsByName.get(parentName);
                if (parent == null) {
                    current.setParentName("");
                    break;
                }
                current.setParentName(parent.getName());
                current = parent;
            }
        }
    }
}
