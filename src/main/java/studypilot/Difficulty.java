package studypilot;

import java.util.Locale;

public enum Difficulty {
    EASY, MEDIUM, HARD;

    public static Difficulty parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Difficulty must be easy, medium or hard.");
        }
    }
}
