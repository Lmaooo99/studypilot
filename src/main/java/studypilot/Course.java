package studypilot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Course {
    private final String courseId;
    private final String name;
    private final List<Material> materials;
    private final List<Concept> concepts = new ArrayList<>();
    private final List<Flashcard> flashcards = new ArrayList<>();
    private final List<StudyEvent> reviewEvents = new ArrayList<>();

    public Course(String name) {
        this(UUID.randomUUID().toString(), name, new ArrayList<Material>());
    }

    public Course(String courseId, String name, List<Material> materials) {
        this.courseId = courseId;
        this.name = name;
        this.materials = new ArrayList<>(materials);
    }

    public String getId() {
        return courseId;
    }

    public String getName() {
        return name;
    }

    public List<Material> getMaterials() {
        return new ArrayList<>(materials);
    }

    public void addMaterial(Material material) {
        materials.add(material);
    }

    public List<Concept> getConcepts() {
        return new ArrayList<>(concepts);
    }

    public void setConcepts(List<Concept> values) {
        concepts.clear();
        concepts.addAll(values);
    }

    public List<Flashcard> getFlashcards() {
        return new ArrayList<>(flashcards);
    }

    public void setFlashcards(List<Flashcard> values) {
        flashcards.clear();
        flashcards.addAll(values);
    }

    public int getTotalReviews() {
        int total = 0;
        for (Flashcard card : flashcards) {
            if (card.getReviewCount() > Integer.MAX_VALUE - total) {
                throw new IllegalArgumentException("The saved review count is too large.");
            }
            total += card.getReviewCount();
        }
        return total;
    }

    public List<StudyEvent> getReviewEvents() {
        return new ArrayList<>(reviewEvents);
    }

    public void addReviewEvent(StudyEvent event) {
        if (!event.getCourseId().equals(courseId)) {
            throw new IllegalArgumentException("A review event must belong to this course.");
        }
        for (StudyEvent previous : reviewEvents) {
            if (previous.getId().equals(event.getId())) {
                throw new IllegalArgumentException("Review event IDs must be distinct.");
            }
        }
        reviewEvents.add(event);
    }

    public Flashcard getFlashcard(String cardId) {
        for (Flashcard card : flashcards) {
            if (card.getId().equals(cardId)) {
                return card;
            }
        }
        throw new IllegalArgumentException("Flashcard not found in this course.");
    }

    public Material getMaterial(String materialId) {
        for (Material material : materials) {
            if (material.getId().equals(materialId)) {
                return material;
            }
        }
        throw new IllegalArgumentException("Material not found in this course.");
    }

    public List<Chunk> getChunks() {
        List<Chunk> chunks = new ArrayList<>();
        for (Material material : materials) {
            chunks.addAll(material.getChunks());
        }
        return chunks;
    }

    public String toString() {
        return name + " [" + courseId.substring(0, 8) + "]";
    }
}
