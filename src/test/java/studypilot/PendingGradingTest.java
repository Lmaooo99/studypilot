package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PendingGradingTest {
    private AppConfig config;
    private Path directory;
    private MockLLMClient llm;
    private AgentFacade facade;
    private Course course;
    private Quiz quiz;
    private Question objective;
    private ShortAnswerQuestion shortAnswer;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-pending-test-"));
        directory = config.getDataDirectory();
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        objective = new TrueFalseQuestion("Observers receive notifications.", "Observer", true);
        shortAnswer = new ShortAnswerQuestion("Explain Observer.", "Observer",
                "A subject notifies subscribers.", "Mention a subject and subscribers.");
        quiz = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, Arrays.asList(objective, shortAnswer));
        new QuizRepository(directory).saveQuiz(quiz);
    }

    private Map<String, String> answers() {
        Map<String, String> answers = new HashMap<>();
        answers.put(objective.getId(), "true");
        answers.put(shortAnswer.getId(), "The subject notifies subscribers.");
        return answers;
    }

    private QuizAttempt pendingAttempt() {
        llm.failNext();
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        return facade.submitAttempt(course.getId(), attempt.getId(), answers());
    }

    @Test
    public void gradesTheShortAnswerOnTheOriginalSubmission() {
        llm.reply("{\"pointsEarned\":1,\"explanation\":\"The required ideas are present.\"}");
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        QuizAttempt graded = facade.submitAttempt(course.getId(), attempt.getId(), answers());
        assertTrue(graded.isFullyGraded());
        assertEquals(100.0, graded.getScore(), 0.0001);
        assertEquals(2, facade.getProgress(course.getId()).getGradedCount());
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void resolvesOnlyThePendingResultAndCountsItOnceAcrossRestarts() {
        QuizAttempt pending = pendingAttempt();
        LocalDateTime submittedAt = pending.getSubmittedAt();
        String objectiveExplanation = pending.getResult(objective.getId()).getExplanation();
        llm.reply("{\"pointsEarned\":1,\"explanation\":\"Complete explanation.\"}");
        QuizAttempt resolved = facade.retryPendingGrading(course.getId(), pending.getId());
        assertEquals(0, resolved.getPendingCount());
        assertEquals(submittedAt, resolved.getSubmittedAt());
        assertEquals(pending.getAnswers(), resolved.getAnswers());
        assertEquals(objectiveExplanation, resolved.getResult(objective.getId()).getExplanation());
        assertEquals(1, facade.getProgress(course.getId()).getQuizzesTaken());
        assertEquals(2, facade.getProgress(course.getId()).getGradedCount());
        assertEquals(2, facade.getProgress(course.getId()).getCorrectCount());
        assertEquals(2, facade.getHistory(course.getId()).size());
        MockLLMClient unused = new MockLLMClient();
        AgentFacade restarted = new AgentFacade(config, unused);
        restarted.retryPendingGrading(course.getId(), pending.getId());
        assertEquals(0, unused.getPrompts().size());
        assertEquals(2, restarted.getProgress(course.getId()).getGradedCount());
        assertEquals(2, restarted.getHistory(course.getId()).size());
    }

    @Test
    public void keepsFailedRegradingPendingAndOutOfStatistics() {
        QuizAttempt pending = pendingAttempt();
        llm.reply("{\"pointsEarned\":9,\"explanation\":\"Invalid score.\"}");
        QuizAttempt retried = facade.retryPendingGrading(course.getId(), pending.getId());
        assertEquals(1, retried.getPendingCount());
        assertFalse(retried.getResult(shortAnswer.getId()).isCounted());
        assertEquals(1, facade.getProgress(course.getId()).getGradedCount());
        assertEquals(1, facade.getHistory(course.getId()).size());
        assertEquals(2, llm.getPrompts().size());
    }

    @Test
    public void previewsTheAnswerAndSelfGradesWithoutAnotherModelCall() {
        QuizAttempt pending = pendingAttempt();
        ShortAnswerQuestion key = facade.getPendingQuestion(course.getId(), pending.getId(), shortAnswer.getId());
        assertEquals(shortAnswer.getCorrectAnswer(), key.getCorrectAnswer());
        assertEquals(shortAnswer.getRubric(), key.getRubric());
        QuizAttempt resolved = facade.selfGrade(course.getId(), pending.getId(), shortAnswer.getId(), true);
        assertEquals(0, resolved.getPendingCount());
        assertTrue(resolved.getResult(shortAnswer.getId()).isCorrect());
        assertTrue(resolved.getResult(shortAnswer.getId()).getExplanation().contains("Student self-assessment"));
        assertEquals(2, facade.getProgress(course.getId()).getGradedCount());
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void selfGradingWrongAddsAGradedIncorrectResultWithoutChangingTheAnswer() {
        QuizAttempt pending = pendingAttempt();
        QuizAttempt resolved = facade.selfGrade(course.getId(), pending.getId(), shortAnswer.getId(), false);
        assertEquals(pending.getAnswers(), resolved.getAnswers());
        assertEquals(50.0, resolved.getScore(), 0.0001);
        assertEquals(2, facade.getProgress(course.getId()).getGradedCount());
        assertEquals(1, facade.getProgress(course.getId()).getCorrectCount());
    }

    @Test
    public void doesNotAllowChangingAnAlreadyGradedAnswer() {
        QuizAttempt pending = pendingAttempt();
        facade.selfGrade(course.getId(), pending.getId(), shortAnswer.getId(), true);
        try {
            facade.selfGrade(course.getId(), pending.getId(), shortAnswer.getId(), false);
            fail("A graded answer must not be changed by a repeated self-grade.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Only pending short answers"));
        }
        assertEquals(2, facade.getProgress(course.getId()).getGradedCount());
        assertEquals(2, facade.getProgress(course.getId()).getCorrectCount());
    }

    @Test
    public void rejectsCrossCourseResolutionBeforeCallingTheModel() {
        QuizAttempt pending = pendingAttempt();
        Course other = facade.createCourse("Other");
        try {
            facade.retryPendingGrading(other.getId(), pending.getId());
            fail("Resolution must stay in the attempt's course.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("this course"));
        }
        assertEquals(1, llm.getPrompts().size());
        assertEquals(1, facade.getAttempt(course.getId(), pending.getId()).getPendingCount());
    }

    @Test
    public void rejectsResolutionBeforeSubmission() {
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        try {
            facade.retryPendingGrading(course.getId(), attempt.getId());
            fail("An unsubmitted attempt cannot be regraded.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Submit the attempt"));
        }
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void retriesHistoryPersistenceEvenWhenTheLastPendingAnswerWasAlreadyGraded() throws Exception {
        QuizAttempt pending = pendingAttempt();
        Path history = directory.resolve("history.json");
        Files.delete(history);
        Files.createDirectory(history);
        Path blocker = history.resolve("test.txt");
        Files.writeString(blocker, "Test-only save obstacle.");
        llm.reply("{\"pointsEarned\":1,\"explanation\":\"Complete answer.\"}");
        try {
            facade.retryPendingGrading(course.getId(), pending.getId());
            fail("The failed history save must be reported.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("could not be saved"));
        }
        Files.delete(blocker);
        Files.delete(history);
        facade.retryPendingGrading(course.getId(), pending.getId());
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals(0, restarted.getAttempt(course.getId(), pending.getId()).getPendingCount());
        assertEquals(2, restarted.getProgress(course.getId()).getGradedCount());
        assertEquals(2, restarted.getHistory(course.getId()).size());
        assertEquals(2, llm.getPrompts().size());
    }
}
