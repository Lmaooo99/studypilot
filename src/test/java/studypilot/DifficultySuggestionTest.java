package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DifficultySuggestionTest {
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-difficulty-test-"));
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
    }

    private QuizAttempt recordAnswers(String topic, int correct, int wrong, int pending) {
        List<Question> questions = new ArrayList<>();
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < correct + wrong; i++) {
            Question question = new TrueFalseQuestion("Statement " + i + " about " + topic, topic, true);
            questions.add(question);
            answers.put(question.getId(), Boolean.toString(i < correct));
        }
        for (int i = 0; i < pending; i++) {
            Question question = new ShortAnswerQuestion("Explain " + topic + " part " + i, topic,
                    "A subject notifies subscribers.", "Mention notifications.");
            questions.add(question);
            answers.put(question.getId(), "A subject notifies subscribers.");
            llm.failNext();
        }
        Quiz quiz = new Quiz(course.getId(), topic, Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        return facade.submitAttempt(course.getId(), attempt.getId(), answers);
    }

    @Test
    public void defaultsToMediumWithoutHistoryOrAnApiCall() {
        DifficultySuggestion suggestion = facade.suggestDifficulty(course.getId(), "Observer");
        assertEquals(Difficulty.MEDIUM, suggestion.getLevel());
        assertEquals(0, suggestion.getGradedCount());
        assertTrue(suggestion.getReason().contains("No graded answers"));
        assertEquals(0, llm.getPrompts().size());
        assertFalse(Files.exists(config.getDataDirectory().resolve("progress.json")));
    }

    @Test
    public void needsFiveGradedAnswersEvenWhenAllFourAreCorrect() {
        recordAnswers("Observer", 4, 0, 0);
        DifficultySuggestion suggestion = facade.suggestDifficulty(course.getId(), "Observer");
        assertEquals(Difficulty.MEDIUM, suggestion.getLevel());
        assertEquals(4, suggestion.getGradedCount());
        assertTrue(suggestion.getReason().contains("At least 5"));
    }

    @Test
    public void selectsEasyBelowFiftyPercent() {
        recordAnswers("Observer", 2, 3, 0);
        DifficultySuggestion suggestion = facade.suggestDifficulty(course.getId(), "Observer");
        assertEquals(Difficulty.EASY, suggestion.getLevel());
        assertEquals(0.4, suggestion.getAccuracy(), 0.0001);
        assertTrue(suggestion.getReason().contains("40% over 5 graded answers"));
    }

    @Test
    public void selectsMediumAtExactlyFiftyPercent() {
        recordAnswers("Observer", 5, 5, 0);
        assertEquals(Difficulty.MEDIUM, facade.suggestDifficulty(course.getId(), "Observer").getLevel());
    }

    @Test
    public void selectsMediumBelowEightyPercent() {
        recordAnswers("Observer", 3, 2, 0);
        assertEquals(Difficulty.MEDIUM, facade.suggestDifficulty(course.getId(), "Observer").getLevel());
    }

    @Test
    public void selectsHardAtExactlyEightyPercent() {
        recordAnswers("Observer", 4, 1, 0);
        assertEquals(Difficulty.HARD, facade.suggestDifficulty(course.getId(), "Observer").getLevel());
    }

    @Test
    public void selectsHardAboveEightyPercent() {
        recordAnswers("Observer", 9, 1, 0);
        assertEquals(Difficulty.HARD, facade.suggestDifficulty(course.getId(), "Observer").getLevel());
    }

    @Test
    public void excludesPendingAnswersFromTheMinimumSample() {
        recordAnswers("Observer", 4, 0, 3);
        DifficultySuggestion suggestion = facade.suggestDifficulty(course.getId(), "Observer");
        assertEquals(4, suggestion.getGradedCount());
        assertEquals(1.0, suggestion.getAccuracy(), 0.0001);
        assertEquals(Difficulty.MEDIUM, suggestion.getLevel());
        assertEquals(3, llm.getPrompts().size());
    }

    @Test
    public void includesAPendingAnswerOnlyAfterItIsResolved() {
        QuizAttempt attempt = recordAnswers("Observer", 4, 0, 1);
        String pendingId = attempt.getResults().get(4).getQuestionId();
        facade.selfGrade(course.getId(), attempt.getId(), pendingId, true);
        DifficultySuggestion suggestion = facade.suggestDifficulty(course.getId(), "Observer");
        assertEquals(5, suggestion.getGradedCount());
        assertEquals(Difficulty.HARD, suggestion.getLevel());
    }

    @Test
    public void matchesTheExactConceptNameIgnoringCaseAndOuterSpaces() {
        recordAnswers("Observer", 5, 0, 0);
        recordAnswers("Observer details", 0, 5, 0);
        assertEquals(Difficulty.HARD, facade.suggestDifficulty(course.getId(), " observer ").getLevel());
        assertEquals(Difficulty.EASY, facade.suggestDifficulty(course.getId(), "Observer details").getLevel());
        assertEquals(0, facade.suggestDifficulty(course.getId(), "Unknown").getGradedCount());
    }

    @Test
    public void keepsOtherCoursesStatisticsSeparate() {
        recordAnswers("Observer", 5, 0, 0);
        Course other = facade.createCourse("Other");
        DifficultySuggestion suggestion = facade.suggestDifficulty(other.getId(), "Observer");
        assertEquals(Difficulty.MEDIUM, suggestion.getLevel());
        assertEquals(0, suggestion.getGradedCount());
    }

    @Test
    public void restoresTheSameSuggestionWithoutWritingProgressOrHistory() throws Exception {
        recordAnswers("Observer", 4, 1, 0);
        Path directory = config.getDataDirectory();
        String progress = Files.readString(directory.resolve("progress.json"));
        String history = Files.readString(directory.resolve("history.json"));
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        assertEquals(Difficulty.HARD, restarted.suggestDifficulty(course.getId(), "Observer").getLevel());
        assertEquals(progress, Files.readString(directory.resolve("progress.json")));
        assertEquals(history, Files.readString(directory.resolve("history.json")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnEmptyTopic() {
        facade.suggestDifficulty(course.getId(), " ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAMissingTopic() {
        facade.suggestDifficulty(course.getId(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnUnknownCourse() {
        facade.suggestDifficulty("missing", "Observer");
    }
}
