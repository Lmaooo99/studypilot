package studypilot;

import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class LLMRubricGradingTest {
    private MockLLMClient llm;
    private LLMRubricGrading grading;
    private ShortAnswerQuestion question;

    @Before
    public void setUp() {
        llm = new MockLLMClient();
        grading = new LLMRubricGrading(llm, new PromptTemplates(), new ResponseParser());
        question = new ShortAnswerQuestion(UUID.randomUUID().toString(), "Explain Observer.", "Observer", 2,
                "The subject notifies subscribers.", "One point for the subject and one for notification.");
    }

    @Test
    public void gradesWithTheModelAnswerRubricAndStudentAnswer() {
        llm.reply("{\"pointsEarned\":2,\"explanation\":\"Both required ideas are present.\"}");
        QuestionResult result = grading.grade(question, "The subject sends updates to subscribers.");
        assertEquals(GradingStatus.GRADED, result.getStatus());
        assertEquals(2, result.getPointsEarned());
        assertTrue(result.isCorrect());
        String prompt = llm.getPrompts().get(0);
        assertTrue(prompt.contains(question.getCorrectAnswer()));
        assertTrue(prompt.contains(question.getRubric()));
        assertTrue(prompt.contains("Maximum points: 2"));
        assertTrue(prompt.contains("The subject sends updates to subscribers."));
    }

    @Test
    public void preservesPartialPointsWithoutCallingThemFullyCorrect() {
        llm.reply("{\"pointsEarned\":1,\"explanation\":\"Notification is missing.\"}");
        QuestionResult result = grading.grade(question, "There is a subject.");
        assertEquals(GradingStatus.GRADED, result.getStatus());
        assertEquals(1, result.getPointsEarned());
        assertFalse(result.isCorrect());
    }

    @Test
    public void leavesInvalidModelGradesPendingWithoutAcceptingCoercedPoints() {
        String[] invalid = {"not JSON", "{\"pointsEarned\":1.5,\"explanation\":\"Partial.\"}",
                "{\"pointsEarned\":1.0,\"explanation\":\"Decimal points.\"}",
                "{\"pointsEarned\":1.0000000000000001,\"explanation\":\"Fractional points.\"}",
                "{\"pointsEarned\":3,\"explanation\":\"Too high.\"}",
                "{\"pointsEarned\":-1,\"explanation\":\"Negative.\"}",
                "{\"pointsEarned\":\"2\",\"explanation\":\"Text points.\"}",
                "{\"pointsEarned\":true,\"explanation\":\"Boolean points.\"}",
                "{\"pointsEarned\":2,\"explanation\":\" \"}"};
        for (String text : invalid) {
            llm.reply(text);
            QuestionResult result = grading.grade(question, "Student answer.");
            assertEquals(GradingStatus.PENDING, result.getStatus());
            assertEquals(0, result.getPointsEarned());
            assertFalse(result.isCounted());
        }
        assertEquals(invalid.length, llm.getPrompts().size());
    }

    @Test
    public void leavesAServiceFailurePendingAfterOneCall() {
        llm.failNext();
        assertEquals(GradingStatus.PENDING, grading.grade(question, "Student answer.").getStatus());
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void gradesAnEmptyAnswerZeroWithoutCallingTheModel() {
        QuestionResult result = grading.grade(question, " ");
        assertEquals(GradingStatus.GRADED, result.getStatus());
        assertEquals(0, result.getPointsEarned());
        assertEquals(0, llm.getPrompts().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnObjectiveQuestionInsteadOfSendingItToTheModel() {
        grading.grade(new TrueFalseQuestion("Observer sends updates.", "Observer", true), "true");
    }
}
