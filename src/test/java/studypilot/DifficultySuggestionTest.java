package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
        return recordAnswers(course, topic, topic, correct, wrong, pending);
    }

    private QuizAttempt recordAnswers(Course target, String topic, String concept, int correct, int wrong, int pending) {
        List<Question> questions = new ArrayList<>();
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < correct + wrong; i++) {
            Question question = new TrueFalseQuestion("Statement " + i + " about " + topic, concept, true);
            questions.add(question);
            answers.put(question.getId(), Boolean.toString(i < correct));
        }
        for (int i = 0; i < pending; i++) {
            Question question = new ShortAnswerQuestion("Explain " + topic + " part " + i, concept,
                    "A subject notifies subscribers.", "Mention notifications.");
            questions.add(question);
            answers.put(question.getId(), "A subject notifies subscribers.");
            llm.failNext();
        }
        Quiz quiz = new Quiz(target.getId(), topic, Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(target.getId(), quiz.getId());
        return facade.submitAttempt(target.getId(), attempt.getId(), answers);
    }

    @Test
    public void keepsConceptStatsWhenTheMatchingQuizHasNoGradedAnswers() {
        recordAnswers(course, "Design patterns", "Observer", 3, 7, 0);
        Question question = new TrueFalseQuestion("Observer receives updates.", "Observer", true);
        Quiz unattempted = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM,
                Arrays.asList(question));
        new QuizRepository(config.getDataDirectory()).saveQuiz(unattempted);
        DifficultySuggestion suggestion = facade.suggestDifficulty(course.getId(), "Observer");
        assertEquals(Difficulty.EASY, suggestion.getLevel());
        assertEquals(10, suggestion.getGradedCount());
        assertEquals(0.3, suggestion.getAccuracy(), 0.0001);
    }

    @Test
    public void usesTheLargerConceptSampleWhenTopicResultsAreSparse() {
        recordAnswers(course, "Design patterns", "Observer", 3, 7, 0);
        recordAnswers("Observer", 2, 0, 0);
        DifficultySuggestion suggestion = facade.suggestDifficulty(course.getId(), "Observer");
        assertEquals(12, suggestion.getGradedCount());
        assertEquals(5.0 / 12, suggestion.getAccuracy(), 0.0001);
        assertEquals(Difficulty.EASY, suggestion.getLevel());
    }

    @Test
    public void prefersTheQuizTopicSampleOnATie() {
        recordAnswers(course, "Design patterns", "Observer", 5, 0, 0);
        recordAnswers(course, "Observer", "Misc", 0, 5, 0);

        DifficultySuggestion suggestion = facade.suggestDifficulty(course.getId(), "Observer");
        assertEquals(5, suggestion.getGradedCount());
        assertEquals(0.0, suggestion.getAccuracy(), 0.0001);
        assertEquals(Difficulty.EASY, suggestion.getLevel());
        ProgressTracker tracker = new ProgressTracker(new ProgressRepository(config.getDataDirectory()));
        assertEquals(5, tracker.getGradedCount(course.getId(), "Observer"));
        assertEquals(1.0, tracker.getAccuracy(course.getId(), "Observer"), 0.0001);
    }

    @Test
    public void keepsMatchingQuizTopicSamplesSeparateBetweenCourses() {
        recordAnswers(course, "Design patterns", "Observer", 5, 0, 0);
        Course other = facade.createCourse("Other");
        recordAnswers(other, "Design patterns", "Strategy", 0, 7, 0);
        DifficultySuggestion first = facade.suggestDifficulty(course.getId(), "Design patterns");
        DifficultySuggestion second = facade.suggestDifficulty(other.getId(), "Design patterns");
        assertEquals(5, first.getGradedCount());
        assertEquals(Difficulty.HARD, first.getLevel());
        assertEquals(7, second.getGradedCount());
        assertEquals(Difficulty.EASY, second.getLevel());
    }

    @Test
    public void readsAnExistingQuizTopicWithoutChangingItsConceptsOrSavedFiles() throws Exception {
        List<Question> questions = new ArrayList<>();
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            String concept = i < 3 ? "Observer" : "Strategy";
            Question question = new TrueFalseQuestion("Design statement " + i, concept, true);
            questions.add(question);
            answers.put(question.getId(), Boolean.toString(i < 4));
        }
        Quiz quiz = new Quiz(course.getId(), "Design patterns", Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        facade.submitAttempt(course.getId(), attempt.getId(), answers);
        facade.submitAttempt(course.getId(), attempt.getId(), answers);
        Path quizFile = config.getDataDirectory().resolve("quizzes").resolve(quiz.getId() + ".json");
        String saved = Files.readString(quizFile);

        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        DifficultySuggestion suggestion = restarted.suggestDifficulty(course.getId(), " design PATTERNS ");
        assertEquals(5, suggestion.getGradedCount());
        assertEquals(0.8, suggestion.getAccuracy(), 0.0001);
        assertEquals(Difficulty.HARD, suggestion.getLevel());
        assertEquals(2, restarted.getProgress(course.getId()).getStats().size());
        assertEquals(3, restarted.suggestDifficulty(course.getId(), "Observer").getGradedCount());
        assertEquals(saved, Files.readString(quizFile));
    }

    @Test
    public void aQuizTopicCountsPendingAnswersOnlyAfterResolution() {
        Question objective = new TrueFalseQuestion("A subject sends updates.", "Observer", true);
        Question pending = new ShortAnswerQuestion("Explain notifications.", "Notification",
                "The subject notifies observers.", "Mention observers.");
        List<Question> questions = new ArrayList<>();
        questions.add(objective);
        questions.add(pending);
        Quiz quiz = new Quiz(course.getId(), "Design patterns", Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        Map<String, String> answers = new HashMap<>();
        answers.put(objective.getId(), "true");
        answers.put(pending.getId(), "Observers receive notifications.");
        llm.failNext();
        facade.submitAttempt(course.getId(), attempt.getId(), answers);
        assertEquals(1, facade.suggestDifficulty(course.getId(), "Design patterns").getGradedCount());
        facade.selfGrade(course.getId(), attempt.getId(), pending.getId(), false);
        DifficultySuggestion suggestion = facade.suggestDifficulty(course.getId(), "Design patterns");
        assertEquals(2, suggestion.getGradedCount());
        assertEquals(0.5, suggestion.getAccuracy(), 0.0001);
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
