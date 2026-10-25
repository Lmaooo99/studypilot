package studypilot;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;

public class FlashcardService {
    private final LLMClient llmClient;
    private final PromptTemplates templates;
    private final ResponseParser parser;
    private final CourseRepository repository;
    private final ProgressTracker tracker;

    public FlashcardService(LLMClient llmClient, PromptTemplates templates, ResponseParser parser,
                            CourseRepository repository, ProgressTracker tracker) {
        this.llmClient = llmClient;
        this.templates = templates;
        this.parser = parser;
        this.repository = repository;
        this.tracker = tracker;
    }

    public List<Flashcard> generateCards(Course course, Material material, int count) {
        if (count < 1 || count > 50) {
            throw new IllegalArgumentException("Request between 1 and 50 flashcards.");
        }
        List<Chunk> chunks = material.getChunks();
        if (chunks.isEmpty() || chunks.size() > 400) {
            throw new IllegalArgumentException("Select a material with 1 to 400 chunks for flashcard generation.");
        }
        String prompt = templates.flashcardPrompt(chunks, count);
        for (int attempt = 0; attempt < 2; attempt++) {
            LLMResponse response = llmClient.generate(prompt);
            if (response.isSuccess()) {
                try {
                    List<Flashcard> proposed = parser.parseFlashcards(response.getText());
                    return saveNewCards(course, proposed, count);
                } catch (JSONException e) {
                    prompt = templates.flashcardPrompt(material.getChunks(), count) + "\nReturn only valid JSON with nonempty fronts and backs.";
                }
            }
        }
        throw new IllegalStateException("Flashcard generation failed after one retry. No cards were saved.");
    }

    private List<Flashcard> saveNewCards(Course course, List<Flashcard> proposed, int count) {
        List<Flashcard> cards = course.getFlashcards();
        List<Flashcard> added = new ArrayList<>();
        for (Flashcard candidate : proposed) {
            if (added.size() == count) {
                break;
            }
            if (!containsFront(cards, candidate.getFront())) {
                cards.add(candidate);
                added.add(candidate);
            }
        }
        if (!added.isEmpty()) {
            course.setFlashcards(cards);
            repository.saveCourse(course);
        }
        return added;
    }

    private boolean containsFront(List<Flashcard> cards, String front) {
        for (Flashcard card : cards) {
            if (card.getFront().equalsIgnoreCase(front)) {
                return true;
            }
        }
        return false;
    }

    public List<Flashcard> getCardsToReview(Course course) {
        List<Flashcard> sorted = new ArrayList<>();
        for (Flashcard card : course.getFlashcards()) {
            int position = 0;
            while (position < sorted.size() && !shouldReviewFirst(card, sorted.get(position))) {
                position++;
            }
            sorted.add(position, card);
        }
        return sorted;
    }

    private boolean shouldReviewFirst(Flashcard first, Flashcard second) {
        if (first.getReviewCount() == 0 && second.getReviewCount() > 0) {
            return true;
        }
        if (second.getReviewCount() == 0 && first.getReviewCount() > 0) {
            return false;
        }
        return first.getMastery() < second.getMastery();
    }

    public void review(Course course, Flashcard card, boolean remembered) {
        Flashcard savedCard = course.getFlashcard(card.getId());
        savedCard.recordReview(remembered);
        repository.saveCourse(course);
        tracker.recordReview(course.getId(), savedCard, remembered);
    }
}
