package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlashcardServiceTest {
    private AppConfig config;
    private MockLLMClient llm;
    private AgentFacade facade;
    private Course course;
    private Material material;

    @Before
    public void setUp() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-card-test-");
        config = new AppConfig(directory);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        Path file = directory.resolve("notes.txt");
        Files.writeString(file, "A facade simplifies access. An observer receives updates.");
        material = facade.importMaterial(course.getId(), file.toString());
    }

    private void replyWithCards() {
        llm.reply("{\"cards\":[{\"front\":\"What is a facade?\",\"back\":\"A simple entry point.\",\"conceptName\":\"Facade\"},"
                + "{\"front\":\"What is an observer?\",\"back\":\"An update receiver.\",\"conceptName\":\"Observer\"},"
                + "{\"front\":\"What is a facade?\",\"back\":\"Duplicate.\"}]}");
    }

    @Test
    public void aFailedCourseSaveDoesNotRecordTheReviewOrChangeTheSavedCourse() throws Exception {
        replyWithCards();
        Flashcard card = facade.generateFlashcards(course.getId(), material.getId(), 2).get(0);
        Path directory = config.getDataDirectory();
        Path file = directory.resolve("courses").resolve(course.getId() + ".json");
        String before = Files.readString(file);
        CourseRepository repository = new CourseRepository(directory) {
            public void saveCourse(Course changed) {
                throw new IllegalStateException("Test primary course save failure.");
            }
        };
        ProgressRepository progress = new ProgressRepository(directory);
        ProgressTracker tracker = new ProgressTracker(progress);
        HistoryRecorder history = new HistoryRecorder(tracker, progress);
        tracker.attach(history);
        FlashcardService service = new FlashcardService(llm, new PromptTemplates(), new ResponseParser(), repository, tracker);
        try {
            service.review(repository.loadCourse(course.getId()), card, true);
            fail("The primary save failure must be reported.");
        } catch (IllegalStateException e) {
            assertEquals("Test primary course save failure.", e.getMessage());
        }
        assertEquals(before, Files.readString(file));
        assertEquals(0, repository.loadCourse(course.getId()).getTotalReviews());
        assertEquals(0, tracker.getSummary(course.getId()).getReviewsDone());
        assertEquals(0, history.getHistory(course.getId()).size());
        assertNull(tracker.getLastEvent());
    }

    @Test
    public void removesDuplicatesAndReportsTheActualNumberOfNewCards() {
        replyWithCards();
        assertEquals(2, facade.generateFlashcards(course.getId(), material.getId(), 5).size());
        replyWithCards();
        assertEquals(0, facade.generateFlashcards(course.getId(), material.getId(), 5).size());
        assertEquals(2, facade.getCardsToReview(course.getId()).size());
        assertEquals(2, llm.getPrompts().size());
    }

    @Test
    public void retriesAnEmptyCardBatchInsteadOfTreatingItAsSuccess() {
        llm.reply("{\"cards\":[]}");
        replyWithCards();
        assertEquals(2, facade.generateFlashcards(course.getId(), material.getId(), 2).size());
        assertEquals(2, llm.getPrompts().size());
        assertEquals(2, facade.getCardsToReview(course.getId()).size());
    }

    @Test
    public void showsTheRealModelErrorWhenBothCallsFail() {
        llm.failNext();
        llm.failNext();
        try {
            facade.generateFlashcards(course.getId(), material.getId(), 2);
            fail("A failed model call must be reported.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No cards were saved"));
            assertTrue(e.getMessage().contains("Last model error: Test service failure."));
        }
    }

    @Test
    public void keepsTheModelFailureWhenTheRetryIsMalformed() {
        llm.failNext();
        llm.reply("not JSON");
        try {
            facade.generateFlashcards(course.getId(), material.getId(), 2);
            fail("The model failure must not be lost.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Last model error: Test service failure."));
        }
        assertEquals(0, facade.getCardsToReview(course.getId()).size());
        assertEquals(2, llm.getPrompts().size());
    }

    @Test
    public void persistsMasteryProgressAndObserverHistoryAcrossRestarts() {
        replyWithCards();
        String id = facade.generateFlashcards(course.getId(), material.getId(), 2).get(0).getId();
        facade.reviewCard(course.getId(), id, true);
        facade.reviewCard(course.getId(), id, false);
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        Flashcard card = restarted.getCourse(course.getId()).getFlashcard(id);
        assertEquals(2, card.getReviewCount());
        assertEquals(0.5, card.getMastery(), 0.0001);
        assertEquals(2, restarted.getProgress(course.getId()).getReviewsDone());
        assertEquals(2, restarted.getHistory(course.getId()).size());
        Course other = restarted.createCourse("Another course");
        assertEquals(0, restarted.getProgress(other.getId()).getReviewsDone());
        assertEquals(0, restarted.getHistory(other.getId()).size());
    }

    @Test
    public void prioritizesNeverReviewedCardsBeforeReviewedCards() {
        replyWithCards();
        List<Flashcard> cards = facade.generateFlashcards(course.getId(), material.getId(), 2);
        facade.reviewCard(course.getId(), cards.get(0).getId(), true);
        assertEquals(cards.get(1).getId(), facade.getCardsToReview(course.getId()).get(0).getId());
    }

    @Test
    public void ordersReviewedCardsByMasteryAndKeepsTiesInTheirOriginalOrder() {
        llm.reply("{\"cards\":[{\"front\":\"First question\",\"back\":\"First answer\"},"
                + "{\"front\":\"Second question\",\"back\":\"Second answer\"},"
                + "{\"front\":\"Third question\",\"back\":\"Third answer\"}]}");
        List<Flashcard> cards = facade.generateFlashcards(course.getId(), material.getId(), 3);
        facade.reviewCard(course.getId(), cards.get(0).getId(), true);
        facade.reviewCard(course.getId(), cards.get(1).getId(), false);
        facade.reviewCard(course.getId(), cards.get(2).getId(), false);

        List<Flashcard> reviewOrder = facade.getCardsToReview(course.getId());
        assertEquals(cards.get(1).getId(), reviewOrder.get(0).getId());
        assertEquals(cards.get(2).getId(), reviewOrder.get(1).getId());
        assertEquals(cards.get(0).getId(), reviewOrder.get(2).getId());
    }

    @Test
    public void doesNotRecordAReviewForAnUnknownCard() {
        try {
            facade.reviewCard(course.getId(), "missing-card", true);
            fail("Unknown cards must be rejected.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
        assertEquals(0, facade.getProgress(course.getId()).getReviewsDone());
        assertEquals(0, facade.getHistory(course.getId()).size());
    }

    @Test
    public void keepsTheValidCardsWhenOneCardIsMalformed() {
        llm.reply("{\"cards\":[{\"front\":\"\",\"back\":\"invalid\"},"
                + "{\"front\":\"What is a facade?\",\"back\":\"A simple entry point.\",\"conceptName\":null}]}");
        assertEquals(1, facade.generateFlashcards(course.getId(), material.getId(), 2).size());
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void retriesMalformedGenerationWithoutSavingInvalidCards() {
        llm.reply("{\"cards\":[{\"front\":\"\",\"back\":\"invalid\"}]}");
        replyWithCards();
        assertEquals(2, facade.generateFlashcards(course.getId(), material.getId(), 5).size());
        assertEquals(2, llm.getPrompts().size());
    }
}
