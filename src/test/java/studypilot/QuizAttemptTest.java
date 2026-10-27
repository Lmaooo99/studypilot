package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuizAttemptTest {
    private Path directory;
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private Quiz quiz;
    private Question choice;
    private Question trueFalse;
    private Question shortAnswer;

    @Before
    public void setUp() throws Exception {
        Path appDirectory = Files.createTempDirectory("studypilot-attempt-test-");
        config = new AppConfig(appDirectory);
        directory = config.getDataDirectory();
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        choice = new MultipleChoiceQuestion("Which pattern gives one entry point?", "Facade",
                Arrays.asList("Facade", "Observer"), 0);
        trueFalse = new TrueFalseQuestion("Observers receive notifications.", "Observer", true);
        shortAnswer = new ShortAnswerQuestion("Explain Observer.", "Observer",
                "The subject notifies subscribers.", "Mention the subject and subscribers.");
        quiz = new Quiz(course.getId(), "Design", Difficulty.MEDIUM, Arrays.asList(choice, trueFalse, shortAnswer));
        new QuizRepository(directory).saveQuiz(quiz);
    }

    private Map<String, String> answers() {
        Map<String, String> answers = new HashMap<>();
        answers.put(choice.getId(), "1");
        answers.put(trueFalse.getId(), "false");
        answers.put(shortAnswer.getId(), "The subject sends notifications.");
        return answers;
    }

    @Test
    public void startsAFreshSavedAttemptEveryTimeWithoutChangingTheQuiz() throws Exception {
        Path file = directory.resolve("quizzes").resolve(quiz.getId() + ".json");
        String original = Files.readString(file);
        QuizAttempt first = facade.startAttempt(course.getId(), quiz.getId());
        QuizAttempt second = facade.startAttempt(course.getId(), quiz.getId());
        assertFalse(first.getId().equals(second.getId()));
        assertFalse(facade.getAttempt(course.getId(), first.getId()).isSubmitted());
        assertEquals(2, facade.listAttempts(course.getId()).size());
        assertEquals(0, facade.getProgress(course.getId()).getQuizzesTaken());
        assertEquals(original, Files.readString(file));
    }

    @Test
    public void usesOnlyGradedPointsInTheScoreAndProgress() {
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        QuizAttempt result = facade.submitAttempt(course.getId(), attempt.getId(), answers());
        assertEquals(1, result.getPointsEarned());
        assertEquals(2, result.getGradedPoints());
        assertEquals(50.0, result.getScore(), 0.0001);
        assertEquals(1, result.getPendingCount());
        assertFalse(result.isFullyGraded());
        assertTrue(result.getResults().get(0).isCounted());
        assertFalse(result.getResults().get(2).isCounted());
        assertEquals(1, facade.getProgress(course.getId()).getQuizzesTaken());
        assertEquals(2, facade.getProgress(course.getId()).getGradedCount());
        assertEquals(1, facade.getProgress(course.getId()).getCorrectCount());
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void gradesAllOmittedAnswersAsZeroIncludingShortAnswers() {
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        QuizAttempt result = facade.submitAttempt(course.getId(), attempt.getId(), new HashMap<String, String>());
        assertEquals(0, result.getPendingCount());
        assertEquals(3, result.getGradedPoints());
        assertEquals(0.0, result.getScore(), 0.0001);
        assertTrue(result.isFullyGraded());
        for (QuestionResult question : result.getResults()) {
            assertTrue(question.getExplanation().startsWith("Unanswered."));
        }
    }

    @Test
    public void repeatedSubmissionAfterRestartKeepsOriginalAnswersTimeAndCounts() {
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        QuizAttempt original = facade.submitAttempt(course.getId(), attempt.getId(), answers());
        LocalDateTime time = original.getSubmittedAt();
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        QuizAttempt repeated = restarted.submitAttempt(course.getId(), attempt.getId(), new HashMap<String, String>());
        assertEquals(original.getAnswers(), repeated.getAnswers());
        assertEquals(time, repeated.getSubmittedAt());
        assertEquals(50.0, repeated.getScore(), 0.0001);
        assertEquals(1, repeated.getPendingCount());
        assertEquals(1, restarted.getProgress(course.getId()).getQuizzesTaken());
        assertEquals(2, restarted.getProgress(course.getId()).getGradedCount());
        assertEquals(1, restarted.getHistory(course.getId()).size());
    }

    @Test
    public void countsRetakesAsSeparateAttemptsEvenWhenQuestionIdsAreTheSame() {
        QuizAttempt first = facade.startAttempt(course.getId(), quiz.getId());
        facade.submitAttempt(course.getId(), first.getId(), answers());
        QuizAttempt second = facade.startAttempt(course.getId(), quiz.getId());
        facade.submitAttempt(course.getId(), second.getId(), answers());
        assertEquals(2, facade.getProgress(course.getId()).getQuizzesTaken());
        assertEquals(4, facade.getProgress(course.getId()).getGradedCount());
        assertEquals(2, facade.getHistory(course.getId()).size());
    }

    @Test
    public void rejectsUnknownAnswerIdsWithoutSubmittingTheAttempt() {
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        Map<String, String> invalid = answers();
        invalid.put(UUID.randomUUID().toString(), "true");
        try {
            facade.submitAttempt(course.getId(), attempt.getId(), invalid);
            fail("Unknown question IDs must be rejected.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Question not found"));
        }
        assertFalse(facade.getAttempt(course.getId(), attempt.getId()).isSubmitted());
        assertEquals(0, facade.getProgress(course.getId()).getQuizzesTaken());
    }

    @Test
    public void rejectsCrossCourseSubmissionAndHidesOtherCoursesAttempts() {
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        Course other = facade.createCourse("Other course");
        assertEquals(0, facade.listAttempts(other.getId()).size());
        try {
            facade.submitAttempt(other.getId(), attempt.getId(), answers());
            fail("An attempt belongs to only one course.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("this course"));
        }
        assertFalse(facade.getAttempt(course.getId(), attempt.getId()).isSubmitted());
    }

    @Test
    public void showsAnUnavailableScoreWhenAllAnswersArePending() {
        Quiz onlyShortAnswer = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, Arrays.asList(shortAnswer));
        new QuizRepository(directory).saveQuiz(onlyShortAnswer);
        QuizAttempt attempt = facade.startAttempt(course.getId(), onlyShortAnswer.getId());
        Map<String, String> answer = new HashMap<>();
        answer.put(shortAnswer.getId(), "Notifications.");
        QuizAttempt result = facade.submitAttempt(course.getId(), attempt.getId(), answer);
        assertEquals(0, result.getGradedPoints());
        assertEquals(1, result.getPendingCount());
        assertTrue(result.describe(onlyShortAnswer).contains("no graded answers"));
        assertEquals(0, facade.getProgress(course.getId()).getGradedCount());
    }

    @Test
    public void rejectsDamagedSubmissionTimesAndPreservesTheSavedFile() throws Exception {
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        facade.submitAttempt(course.getId(), attempt.getId(), answers());
        Path file = directory.resolve("attempts").resolve(attempt.getId() + ".json");
        JSONObject data = new JSONObject(Files.readString(file));
        data.put("submittedAt", "invalid time");
        String damaged = data.toString();
        Files.writeString(file, damaged);
        try {
            facade.getAttempt(course.getId(), attempt.getId());
            fail("A damaged timestamp must not crash or silently change the attempt.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("original file was not changed"));
        }
        assertEquals(damaged, Files.readString(file));
    }

    @Test
    public void repeatedSubmissionRetriesFailedHistoryPersistenceWithoutRecounting() throws Exception {
        Path blocked = directory.resolve("history.json");
        Files.createDirectory(blocked);
        Path blocker = blocked.resolve("test.txt");
        Files.writeString(blocker, "Test-only obstacle to a JSON save.");
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        try {
            facade.submitAttempt(course.getId(), attempt.getId(), answers());
            fail("The failed history save must be reported.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("could not be saved"));
        }
        Files.delete(blocker);
        Files.delete(blocked);
        facade.submitAttempt(course.getId(), attempt.getId(), new HashMap<String, String>());
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals(1, restarted.getProgress(course.getId()).getQuizzesTaken());
        assertEquals(2, restarted.getProgress(course.getId()).getGradedCount());
        assertEquals(1, restarted.getHistory(course.getId()).size());
        assertTrue(restarted.getAttempt(course.getId(), attempt.getId()).getResults().get(0).isCounted());
    }
}
