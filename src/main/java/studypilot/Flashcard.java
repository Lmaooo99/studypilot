package studypilot;

import java.util.UUID;

public class Flashcard {
    private final String cardId;
    private final String front;
    private final String back;
    private final String conceptName;
    private int reviewCount;
    private int rememberedCount;
    public Flashcard(String front, String back, String conceptName) {
        this(UUID.randomUUID().toString(), front, back, conceptName, 0, 0);
    }

    public Flashcard(String cardId, String front, String back, String conceptName, int reviewCount, int rememberedCount) {
        if (reviewCount < 0 || rememberedCount < 0 || rememberedCount > reviewCount) {
            throw new IllegalArgumentException("Invalid saved flashcard review counts.");
        }
        this.cardId = cardId;
        this.front = front.trim();
        this.back = back.trim();
        this.conceptName = conceptName.trim();
        this.reviewCount = reviewCount;
        this.rememberedCount = rememberedCount;
    }

    public String getId() {
        return cardId;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public String getConceptName() {
        return conceptName;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public int getRememberedCount() {
        return rememberedCount;
    }

    public void recordReview(boolean remembered) {
        reviewCount++;
        if (remembered) {
            rememberedCount++;
        }
    }

    public double getMastery() {
        if (reviewCount == 0) {
            return 0;
        }
        return (double) rememberedCount / reviewCount;
    }

    public String toString() {
        return front;
    }
}
