package studypilot;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class LearningFeedbackIntegrationTest {
    private AppConfig config;
    private MockLLMClient llm;
    private AgentFacade facade;
    private Course course;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-feedback-integration-"));
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
    }

    private QuizAttempt submitWithPendingAnswers(int correct, int wrong, int pending) {
        List<Question> questions = new ArrayList<>();
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < correct + wrong; i++) {
            Question question = new TrueFalseQuestion("Observer statement " + i, "Observer", true);
            questions.add(question);
            answers.put(question.getId(), Boolean.toString(i < correct));
        }
        for (int i = 0; i < pending; i++) {
            Question question = new ShortAnswerQuestion("Explain Observer part " + i, "Observer",
                    "The subject notifies subscribers.", "Mention notifications.");
            questions.add(question);
            answers.put(question.getId(), "The subject notifies subscribers.");
            llm.failNext();
        }
        Quiz quiz = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        return facade.submitAttempt(course.getId(), attempt.getId(), answers);
    }

    @Test
    public void aPendingIncorrectAnswerEntersTheEightyPercentBoundaryOnlyAfterGrading() {
        QuizAttempt attempt = submitWithPendingAnswers(4, 0, 1);
        MockLLMClient reader = new MockLLMClient();
        AgentFacade restarted = new AgentFacade(config, reader);
        assertEquals(Difficulty.MEDIUM, restarted.suggestDifficulty(course.getId(), "Observer").getLevel());
        assertEquals(4, restarted.getProgress(course.getId()).getGradedCount());
        assertEquals(100.0, attempt.getScore(), 0.0001);
        assertTrue(restarted.getWeakTopics(course.getId()).getRankedTopics().isEmpty());
        assertEquals(0, reader.getPrompts().size());
        String pendingId = attempt.getResults().get(4).getQuestionId();
        QuizAttempt resolved = restarted.selfGrade(course.getId(), attempt.getId(), pendingId, false);
        DifficultySuggestion suggestion = restarted.suggestDifficulty(course.getId(), "Observer");
        assertEquals(5, suggestion.getGradedCount());
        assertEquals(0.8, suggestion.getAccuracy(), 0.0001);
        assertEquals(Difficulty.HARD, suggestion.getLevel());
        reader.reply("Four of five answers were correct; this is the measured result.");
        ConceptStats topic = restarted.getWeakTopics(course.getId()).getRankedTopics().get(0);
        assertEquals(5, topic.getGradedCount());
        assertEquals(4, topic.getCorrectCount());
        assertEquals(attempt.getSubmittedAt(), resolved.getSubmittedAt());
        assertEquals(attempt.getAnswers(), resolved.getAnswers());
        new AgentFacade(config, reader).retryPendingGrading(course.getId(), attempt.getId());
        assertEquals(1, reader.getPrompts().size());
        assertEquals(1, restarted.getProgress(course.getId()).getQuizzesTaken());
    }

    @Test
    public void twoPendingResolutionsMoveFromEasyToExactlyFiftyPercentAcrossRestarts() {
        QuizAttempt attempt = submitWithPendingAnswers(2, 2, 2);
        MockLLMClient unused = new MockLLMClient();
        AgentFacade first = new AgentFacade(config, unused);
        first.selfGrade(course.getId(), attempt.getId(), attempt.getResults().get(4).getQuestionId(), false);
        DifficultySuggestion lower = first.suggestDifficulty(course.getId(), "Observer");
        assertEquals(5, lower.getGradedCount());
        assertEquals(0.4, lower.getAccuracy(), 0.0001);
        assertEquals(Difficulty.EASY, lower.getLevel());
        AgentFacade second = new AgentFacade(config, unused);
        second.selfGrade(course.getId(), attempt.getId(), attempt.getResults().get(5).getQuestionId(), true);
        AgentFacade third = new AgentFacade(config, unused);
        DifficultySuggestion exact = third.suggestDifficulty(course.getId(), "Observer");
        assertEquals(6, exact.getGradedCount());
        assertEquals(0.5, exact.getAccuracy(), 0.0001);
        assertEquals(Difficulty.MEDIUM, exact.getLevel());
        QuizAttempt restored = third.getAttempt(course.getId(), attempt.getId());
        assertEquals(attempt.getAnswers(), restored.getAnswers());
        assertEquals(attempt.getSubmittedAt(), restored.getSubmittedAt());
        assertEquals(0, restored.getPendingCount());
        assertEquals(1, third.getProgress(course.getId()).getQuizzesTaken());
        assertEquals(3, third.getHistory(course.getId()).size());
        assertEquals(0, unused.getPrompts().size());
    }

    @Test
    public void anInvalidRubricReplyStaysPendingUntilAValidRetryUpdatesDifficulty() {
        QuizAttempt attempt = submitWithPendingAnswers(4, 0, 1);
        MockLLMClient grader = new MockLLMClient();
        grader.reply("{\"pointsEarned\":1.5,\"explanation\":\"Invalid fractional score.\"}");
        AgentFacade restarted = new AgentFacade(config, grader);
        QuizAttempt pending = restarted.retryPendingGrading(course.getId(), attempt.getId());
        assertEquals(1, pending.getPendingCount());
        assertEquals(4, restarted.getProgress(course.getId()).getGradedCount());
        assertEquals(Difficulty.MEDIUM, restarted.suggestDifficulty(course.getId(), "Observer").getLevel());
        grader.reply("{\"pointsEarned\":1,\"explanation\":\"The required idea is present.\"}");
        QuizAttempt resolved = new AgentFacade(config, grader).retryPendingGrading(course.getId(), attempt.getId());
        AgentFacade reader = new AgentFacade(config, grader);
        assertEquals(0, resolved.getPendingCount());
        assertEquals(Difficulty.HARD, reader.suggestDifficulty(course.getId(), "Observer").getLevel());
        assertEquals(5, reader.getProgress(course.getId()).getGradedCount());
        reader.retryPendingGrading(course.getId(), attempt.getId());
        assertEquals(2, grader.getPrompts().size());
        assertEquals(attempt.getSubmittedAt(), resolved.getSubmittedAt());
        assertEquals(attempt.getAnswers(), resolved.getAnswers());
    }

    @Test
    public void partialCreditAffectsTheScoreButDoesNotCountAsAFullyCorrectAnswer() {
        List<Question> questions = new ArrayList<>();
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            Question question = new ShortAnswerQuestion(UUID.randomUUID().toString(), "Explain Observer part " + i,
                    "Observer", 2, "The subject notifies subscribers.", "Mention the subject and subscribers.");
            questions.add(question);
            answers.put(question.getId(), "Notifications.");
            llm.reply("{\"pointsEarned\":1,\"explanation\":\"One required idea is missing.\"}");
        }
        Quiz quiz = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        QuizAttempt graded = facade.submitAttempt(course.getId(), attempt.getId(), answers);
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals(50.0, graded.getScore(), 0.0001);
        assertEquals(5, restarted.getProgress(course.getId()).getGradedCount());
        assertEquals(0, restarted.getProgress(course.getId()).getCorrectCount());
        assertEquals(Difficulty.EASY, restarted.suggestDifficulty(course.getId(), "Observer").getLevel());
        assertEquals(0.0, restarted.getWeakTopics(course.getId()).getRankedTopics().get(0).getAccuracy(), 0.0001);
    }
}
