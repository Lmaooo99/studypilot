package studypilot;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuestionCreatorTest {
    @Test
    public void createsAMultipleChoiceQuestionWithTheMatchingAnswer() {
        RawQuestion raw = new RawQuestion(QuestionType.MULTIPLE_CHOICE, "Which pattern gives one entry point?",
                Arrays.asList("Observer", "Facade", "Adapter"), "Facade", "", "Facade");
        Question question = new MCQQuestionCreator().buildQuestion(raw);
        assertTrue(question instanceof MultipleChoiceQuestion);
        assertEquals(1, ((MultipleChoiceQuestion) question).getCorrectIndex());
        assertEquals("Facade", question.getCorrectAnswer());
        assertEquals(1, question.getPoints());
    }

    @Test
    public void createsATrueFalseQuestionWithoutChangingFalseToTrue() {
        RawQuestion raw = new RawQuestion(QuestionType.TRUE_FALSE, "A facade is an observer.",
                new ArrayList<String>(), "false", "", "Facade");
        Question question = new TrueFalseQuestionCreator().buildQuestion(raw);
        assertTrue(question instanceof TrueFalseQuestion);
        assertEquals("false", question.getCorrectAnswer());
    }

    @Test
    public void createsAShortAnswerQuestionWithItsRubric() {
        RawQuestion raw = new RawQuestion(QuestionType.SHORT_ANSWER, "Explain Observer.",
                new ArrayList<String>(), "It notifies subscribers.", "Mention notification and subscribers.", "Observer");
        Question question = new ShortAnswerQuestionCreator().buildQuestion(raw);
        assertTrue(question instanceof ShortAnswerQuestion);
        assertEquals("It notifies subscribers.", question.getCorrectAnswer());
        assertEquals("Mention notification and subscribers.", ((ShortAnswerQuestion) question).getRubric());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyQuestionTextInTheCommonBuildOperation() {
        new TrueFalseQuestionCreator().buildQuestion(new RawQuestion(QuestionType.TRUE_FALSE, " ",
                new ArrayList<String>(), "true", "", "Observer"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateOptionsIgnoringCaseAndOuterSpaces() {
        new MCQQuestionCreator().buildQuestion(new RawQuestion(QuestionType.MULTIPLE_CHOICE, "Choose a pattern.",
                Arrays.asList("Facade", " facade "), "Facade", "", "Facade"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnAnswerThatDoesNotMatchAnOption() {
        new MCQQuestionCreator().buildQuestion(new RawQuestion(QuestionType.MULTIPLE_CHOICE, "Choose a pattern.",
                Arrays.asList("Facade", "Observer"), "Adapter", "", "Facade"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnAmbiguousTrueFalseAnswer() {
        new TrueFalseQuestionCreator().buildQuestion(new RawQuestion(QuestionType.TRUE_FALSE, "Observer sends updates.",
                new ArrayList<String>(), "perhaps", "", "Observer"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAShortAnswerWithoutARubric() {
        new ShortAnswerQuestionCreator().buildQuestion(new RawQuestion(QuestionType.SHORT_ANSWER, "Explain Observer.",
                new ArrayList<String>(), "It sends updates.", " ", "Observer"));
    }
}
