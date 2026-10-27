package studypilot;

import java.util.List;

public class MCQQuestionCreator extends QuestionCreator {
    protected Question createQuestion(RawQuestion raw) {
        if (raw.getType() != QuestionType.MULTIPLE_CHOICE) {
            throw new IllegalArgumentException("Expected a multiple-choice question.");
        }
        List<String> options = raw.getOptions();
        int correctIndex = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).trim().equalsIgnoreCase(raw.getCorrectAnswer().trim())) {
                correctIndex = i;
            }
        }
        return new MultipleChoiceQuestion(raw.getText(), raw.getConceptName(), options, correctIndex);
    }
}
