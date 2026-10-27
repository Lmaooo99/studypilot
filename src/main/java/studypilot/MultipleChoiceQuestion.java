package studypilot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MultipleChoiceQuestion extends Question {
    private final List<String> options;
    private final int correctIndex;

    public MultipleChoiceQuestion(String text, String conceptName, List<String> options, int correctIndex) {
        this(UUID.randomUUID().toString(), text, conceptName, 1, options, correctIndex);
    }

    public MultipleChoiceQuestion(String id, String text, String conceptName, int points,
                                  List<String> options, int correctIndex) {
        super(id, text, conceptName, points);
        if (options.size() < 2 || correctIndex < 0 || correctIndex >= options.size()) {
            throw new IllegalArgumentException("A multiple-choice question needs options and one correct option.");
        }
        this.options = new ArrayList<>();
        for (String option : options) {
            if (option.isBlank()) {
                throw new IllegalArgumentException("Question options cannot be empty.");
            }
            for (String previous : this.options) {
                if (previous.equalsIgnoreCase(option.trim())) {
                    throw new IllegalArgumentException("Question options must be different.");
                }
            }
            this.options.add(option.trim());
        }
        this.correctIndex = correctIndex;
    }

    public List<String> getOptions() {
        return new ArrayList<>(options);
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public QuestionType getType() {
        return QuestionType.MULTIPLE_CHOICE;
    }

    public String getCorrectAnswer() {
        return options.get(correctIndex);
    }
}
