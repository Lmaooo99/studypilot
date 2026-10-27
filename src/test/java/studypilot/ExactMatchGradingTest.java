package studypilot;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExactMatchGradingTest {
    @Test
    public void gradesTheDisplayedMultipleChoiceNumber() {
        Question question = new MultipleChoiceQuestion("Which pattern receives updates?", "Observer",
                Arrays.asList("Facade", "Adapter", "Observer"), 2);
        QuestionResult result = new ExactMatchGrading().grade(question, " 3 ");
        assertEquals(GradingStatus.GRADED, result.getStatus());
        assertTrue(result.isCorrect());
        assertEquals(1, result.getPointsEarned());
    }

    @Test
    public void explainsAnIncorrectMultipleChoiceAnswerFromTheStoredKey() {
        Question question = new MultipleChoiceQuestion("Which pattern receives updates?", "Observer",
                Arrays.asList("Facade", "Observer"), 1);
        QuestionResult result = new ExactMatchGrading().grade(question, "1");
        assertFalse(result.isCorrect());
        assertEquals(0, result.getPointsEarned());
        assertEquals("Correct answer: 2) Observer", result.getExplanation());
    }

    @Test
    public void acceptsCaseAndOuterSpacesForTrueFalseAnswers() {
        Question question = new TrueFalseQuestion("A facade is an observer.", "Facade", false);
        assertTrue(new ExactMatchGrading().grade(question, " FALSE ").isCorrect());
        assertFalse(new ExactMatchGrading().grade(question, "yes").isCorrect());
    }

    @Test
    public void flagsAnUnansweredObjectiveQuestionAndAwardsZero() {
        Question question = new TrueFalseQuestion("Observers receive updates.", "Observer", true);
        QuestionResult result = new ExactMatchGrading().grade(question, " ");
        assertEquals(0, result.getPointsEarned());
        assertTrue(result.getExplanation().startsWith("Unanswered."));
    }

    @Test(expected = IllegalArgumentException.class)
    public void doesNotPretendToGradeAShortAnswerByExactMatching() {
        Question question = new ShortAnswerQuestion("Explain Observer.", "Observer", "Notifications.", "Mention subscribers.");
        new ExactMatchGrading().grade(question, "Notifications.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void preventsPendingResultsFromBeingStoredAsCounted() {
        Question question = new TrueFalseQuestion("Observers receive updates.", "Observer", true);
        new QuestionResult(question, GradingStatus.PENDING, 0, "Pending.", true);
    }
}
