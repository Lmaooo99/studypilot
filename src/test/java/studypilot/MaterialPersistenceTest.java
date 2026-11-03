package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class MaterialPersistenceTest {
    private Path workspace;
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;

    @Before
    public void setUp() throws Exception {
        workspace = Files.createTempDirectory("studypilot-material-persistence-");
        config = new AppConfig(workspace);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
    }

    private Path writeNotes(String name, String text) throws Exception {
        Path file = workspace.resolve(name);
        Files.writeString(file, text);
        return file;
    }

    private String citedAnswer(String answer, String... ids) {
        return new JSONObject().put("answer", answer)
                .put("citedChunkIds", new JSONArray(ids)).toString();
    }

    @Test
    public void retainsTextIdsAndLineCitationsWhenTheOriginalFileIsRemoved() throws Exception {
        Path file = writeNotes("notes.md", "\uFEFFObserver notifies subscribers.\r\n\r\nFacade has one entry point.");
        Material imported = facade.importMaterial(course.getId(), file.toString());
        Chunk original = imported.getChunks().get(0);
        Files.delete(file);
        MockLLMClient reader = new MockLLMClient();
        AgentFacade restarted = new AgentFacade(config, reader);
        Material restored = restarted.getCourse(course.getId()).getMaterial(imported.getId());
        assertEquals(imported.getContentHash(), restored.getContentHash());
        assertEquals(original.getId(), restored.getChunks().get(0).getId());
        assertEquals("Observer notifies subscribers.\n\nFacade has one entry point.", restored.getChunks().get(0).getText());
        assertEquals(0, reader.getPrompts().size());
        reader.reply(citedAnswer("Observers receive notifications.", original.getId()));
        Citation citation = restarted.askQuestion(course.getId(), "Observer").getCitations().get(0);
        assertEquals(original.getId(), citation.getChunkId());
        assertEquals("notes.md", citation.getSourceFile());
        assertEquals(0, citation.getPage());
        assertEquals(1, citation.getLineStart());
        assertEquals(3, citation.getLineEnd());
    }

    private void writePdfWithABlankMiddlePage(Path file) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int number = 1; number <= 3; number++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (number == 2) {
                    continue;
                }
                try (PDPageContentStream text = new PDPageContentStream(document, page)) {
                    text.beginText();
                    text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    text.newLineAtOffset(40, 700);
                    if (number == 1) {
                        text.showText("Observer notifies subscribers.");
                    } else {
                        text.showText("Facade provides one entry point.");
                    }
                    text.endText();
                }
            }
            document.save(file.toFile());
        }
    }

    @Test
    public void preservesPdfPageNumbersAcrossBlankPagesAndACitedAnswer() throws Exception {
        Path file = workspace.resolve("lecture.pdf");
        writePdfWithABlankMiddlePage(file);
        Material imported = facade.importMaterial(course.getId(), file.toString());
        assertEquals(2, imported.getChunks().size());
        Chunk thirdPage = imported.getChunks().get(1);
        assertEquals(3, thirdPage.getPage());
        Files.delete(file);
        MockLLMClient reader = new MockLLMClient();
        reader.reply(citedAnswer("Facade provides one entry point.", thirdPage.getId()));
        AgentFacade restarted = new AgentFacade(config, reader);
        Material restored = restarted.getCourse(course.getId()).getMaterial(imported.getId());
        assertEquals(imported.getChunks().get(0).getId(), restored.getChunks().get(0).getId());
        assertEquals(1, restored.getChunks().get(0).getPage());
        Citation citation = restarted.askQuestion(course.getId(), "Facade").getCitations().get(0);
        assertEquals(thirdPage.getId(), citation.getChunkId());
        assertEquals("lecture.pdf", citation.getSourceFile());
        assertEquals(3, citation.getPage());
        assertEquals(0, citation.getLineStart());
        assertEquals(0, citation.getLineEnd());
    }

    @Test
    public void laterCourseWritesKeepSourcesSummariesConceptsAndReviewedCards() throws Exception {
        Material imported = facade.importMaterial(course.getId(), writeNotes("observer.txt", "Observer notifies subscribers.").toString());
        String sourceId = imported.getChunks().get(0).getId();
        llm.reply("Observer sends notifications to subscribers.");
        facade.summarize(course.getId(), imported.getId(), "brief");
        JSONObject concept = new JSONObject().put("name", "Observer").put("definition", "Notify subscribers.")
                .put("parentName", "").put("sourceChunkIds", new JSONArray().put(sourceId));
        llm.reply(new JSONObject().put("concepts", new JSONArray().put(concept)).toString());
        new AgentFacade(config, llm).extractConcepts(course.getId(), imported.getId());
        llm.reply("{\"cards\":[{\"front\":\"Who receives updates?\",\"back\":\"Subscribers.\",\"conceptName\":\"Observer\"}]}");
        AgentFacade reviewer = new AgentFacade(config, llm);
        Flashcard card = reviewer.generateFlashcards(course.getId(), imported.getId(), 1).get(0);
        reviewer.reviewCard(course.getId(), card.getId(), true);
        StudyEvent event = reviewer.getHistory(course.getId()).get(0);
        reviewer.importMaterial(course.getId(), writeNotes("facade.md", "Facade offers one entry point.").toString());
        MockLLMClient reader = new MockLLMClient();
        AgentFacade restarted = new AgentFacade(config, reader);
        Course restored = restarted.getCourse(course.getId());
        assertEquals(2, restored.getMaterials().size());
        Material original = restored.getMaterial(imported.getId());
        assertEquals(imported.getContentHash(), original.getContentHash());
        assertEquals(sourceId, original.getChunks().get(0).getId());
        assertEquals("Observer sends notifications to subscribers.", original.getSummary());
        assertEquals(sourceId, restored.getConcepts().get(0).getSourceChunkIds().get(0));
        assertEquals(1, restored.getFlashcard(card.getId()).getReviewCount());
        assertEquals(1.0, restored.getFlashcard(card.getId()).getMastery(), 0.0001);
        assertEquals(event.getId(), restored.getReviewEvents().get(0).getId());
        assertEquals(event.getTime(), restarted.getHistory(course.getId()).get(0).getTime());
        assertEquals(0, reader.getPrompts().size());
    }

    @Test
    public void detectsRenamedDuplicateContentOnlyWithinItsCourseAfterRestart() throws Exception {
        Path file = writeNotes("original.txt", "Observer notifies subscribers.");
        Material first = facade.importMaterial(course.getId(), file.toString());
        Path renamed = workspace.resolve("renamed.md");
        Files.copy(file, renamed);
        Path saved = config.getDataDirectory().resolve("courses").resolve(course.getId() + ".json");
        String before = Files.readString(saved);
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        try {
            restarted.importMaterial(course.getId(), renamed.toString());
            fail("Renaming a file must not bypass content deduplication.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(first.getId()));
        }
        assertEquals(before, Files.readString(saved));
        Course other = restarted.createCourse("Other course");
        Material second = restarted.importMaterial(other.getId(), renamed.toString());
        assertEquals(first.getContentHash(), second.getContentHash());
        assertFalse(first.getId().equals(second.getId()));
        assertFalse(first.getChunks().get(0).getId().equals(second.getChunks().get(0).getId()));
    }

    @Test
    public void distinguishesIdenticalFileNamesAndRejectsAnotherCoursesCitation() throws Exception {
        Path firstDirectory = Files.createDirectory(workspace.resolve("first"));
        Path secondDirectory = Files.createDirectory(workspace.resolve("second"));
        Path firstFile = firstDirectory.resolve("notes.txt");
        Path secondFile = secondDirectory.resolve("notes.txt");
        Files.writeString(firstFile, "Observer notifies subscribers.");
        Files.writeString(secondFile, "Observer uses a subject and subscribers.");
        Material first = facade.importMaterial(course.getId(), firstFile.toString());
        Material second = facade.importMaterial(course.getId(), secondFile.toString());
        Course other = facade.createCourse("Other");
        Material foreign = facade.importMaterial(other.getId(), firstFile.toString());
        String sourceId = second.getChunks().get(0).getId();
        MockLLMClient reader = new MockLLMClient();
        reader.reply(citedAnswer("Observer uses a subject and subscribers.", foreign.getChunks().get(0).getId(), sourceId));
        AgentFacade restarted = new AgentFacade(config, reader);
        List<Citation> citations = restarted.askQuestion(course.getId(), "Observer").getCitations();
        assertEquals(2, restarted.getCourse(course.getId()).getMaterials().size());
        assertFalse(first.getChunks().get(0).getId().equals(sourceId));
        assertEquals(1, citations.size());
        assertEquals(sourceId, citations.get(0).getChunkId());
        assertEquals("notes.txt", citations.get(0).getSourceFile());
    }
}
