package studypilot;

import java.util.ArrayList;
import java.util.List;

public class RawQuestion {
    private final QuestionType type;
    private final String text;
    private final List<String> options;
    private final String correctAnswer;
    private final String rubric;
    private final String conceptName;

    public RawQuestion(QuestionType type, String text, List<String> options,
                       String correctAnswer, String rubric, String conceptName) {
        this.type = type;
        this.text = text;
        this.options = new ArrayList<>(options);
        this.correctAnswer = correctAnswer;
        this.rubric = rubric;
        this.conceptName = conceptName;
    }

    public QuestionType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public List<String> getOptions() {
        return new ArrayList<>(options);
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String getRubric() {
        return rubric;
    }

    public String getConceptName() {
        return conceptName;
    }
}
