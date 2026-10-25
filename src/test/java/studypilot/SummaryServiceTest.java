package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class SummaryServiceTest {
    private MockLLMClient llm;
    private CourseRepository repository;
    private SummaryService service;
    private Course course;
    private Material material;

    @Before
    public void setUp() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-summary-test-");
        repository = new CourseRepository(directory);
        llm = new MockLLMClient();
        service = new SummaryService(llm, new PromptTemplates(), repository);
        course = new Course("Design");
        List<Chunk> chunks = new ArrayList<>();
        chunks.add(new Chunk("A facade simplifies access.", "notes.txt", 0, 1, 1));
        material = new Material("notes.txt", "test-content-hash", chunks);
        course.addMaterial(material);
        repository.saveCourse(course);
    }

    @Test
    public void storesTheGeneratedSummary() {
        llm.reply("A facade offers a simple entry point.");
        String text = service.summarize(course, material, "brief");
        assertEquals(text, repository.loadCourse(course.getId()).getMaterial(material.getId()).getSummary());
    }

    @Test
    public void retriesOneFailureAndThenUsesTheSuccessfulResponse() {
        llm.failNext();
        llm.reply("A short summary.");
        assertEquals("A short summary.", service.summarize(course, material, "bullet"));
        assertEquals(2, llm.getPrompts().size());
    }

    @Test
    public void preservesTheSavedSummaryWhenBothCallsFail() {
        material.setSummary("Earlier summary");
        repository.saveCourse(course);
        llm.failNext();
        llm.failNext();
        try {
            service.summarize(course, material, "brief");
            fail("Failed calls must not overwrite saved work.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("could not be generated"));
        }
        assertEquals("Earlier summary", repository.loadCourse(course.getId()).getMaterial(material.getId()).getSummary());
    }

    @Test
    public void mergesBatchesForLongMaterials() {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            chunks.add(new Chunk("Part " + i, "long.txt", 0, i + 1, i + 1));
        }
        Material longMaterial = new Material("long.txt", "long-test-hash", chunks);
        course.addMaterial(longMaterial);
        llm.reply("First part");
        llm.reply("Second part");
        llm.reply("Combined summary");
        assertEquals("Combined summary", service.summarize(course, longMaterial, "detailed"));
        assertEquals(3, llm.getPrompts().size());
    }
}
