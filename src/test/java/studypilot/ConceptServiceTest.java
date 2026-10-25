package studypilot;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConceptServiceTest {
    private MockLLMClient llm;
    private CourseRepository repository;
    private ConceptService service;
    private Course course;
    private Material material;
    private String chunkId;

    @Before
    public void setUp() throws Exception {
        repository = new CourseRepository(Files.createTempDirectory("studypilot-concept-test-"));
        llm = new MockLLMClient();
        service = new ConceptService(llm, new PromptTemplates(), new ResponseParser(), repository);
        course = new Course("Design");
        List<Chunk> chunks = new ArrayList<>();
        chunks.add(new Chunk("Observer and Strategy.", "notes.txt", 0, 1, 1));
        chunkId = chunks.get(0).getId();
        material = new Material("notes.txt", "test-content-hash", chunks);
        course.addMaterial(material);
        repository.saveCourse(course);
    }

    private JSONObject concept(String name, String parent, String source) {
        JSONObject concept = new JSONObject();
        concept.put("name", name);
        concept.put("definition", "A design concept.");
        concept.put("parentName", parent);
        JSONArray sources = new JSONArray();
        sources.put(source);
        concept.put("sourceChunkIds", sources);
        return concept;
    }

    @Test
    public void mergesDuplicateNamesAndDropsCyclicParentLinks() {
        JSONArray items = new JSONArray();
        items.put(concept("Observer", "Strategy", chunkId));
        items.put(concept("observer", "", chunkId));
        items.put(concept("Strategy", "Observer", chunkId));
        llm.reply(new JSONObject().put("concepts", items).toString());
        List<Concept> concepts = service.extractConcepts(course, material);
        assertEquals(2, concepts.size());
        assertTrue(concepts.get(0).getParentName().isEmpty() || concepts.get(1).getParentName().isEmpty());
        assertEquals(2, repository.loadCourse(course.getId()).getConcepts().size());
    }

    @Test
    public void retriesMalformedJsonWithAStricterPrompt() {
        llm.reply("Not JSON");
        llm.reply(new JSONObject().put("concepts", new JSONArray().put(concept("Observer", "", chunkId))).toString());
        assertEquals(1, service.extractConcepts(course, material).size());
        assertEquals(2, llm.getPrompts().size());
        assertTrue(llm.getPrompts().get(1).contains("Return only valid JSON"));
    }

    @Test
    public void rejectsInventedSourceIdsWithoutSavingPartialData() {
        String invalid = new JSONObject().put("concepts", new JSONArray().put(concept("Observer", "", "invented-id"))).toString();
        llm.reply(invalid);
        llm.reply(invalid);
        try {
            service.extractConcepts(course, material);
            fail("Invented sources must be rejected.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No concepts were saved"));
        }
        assertEquals(0, repository.loadCourse(course.getId()).getConcepts().size());
    }

    @Test
    public void preservesEarlierConceptsWhenTheModelFindsNothing() {
        llm.reply(new JSONObject().put("concepts", new JSONArray().put(concept("Observer", "", chunkId))).toString());
        service.extractConcepts(course, material);
        llm.reply("{\"concepts\":[]}");
        assertEquals(0, service.extractConcepts(course, material).size());
        assertEquals(1, repository.loadCourse(course.getId()).getConcepts().size());
    }
}
