package studypilot;

import java.util.Locale;

public enum QuestionType {
    MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER;

    public static QuestionType parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Question types must be multiple-choice, true-false or short-answer.");
        }
    }
}
