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

public class WeakTopicAnalyzerTest {
    private Path directory;
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("studypilot-weak-topics-test-");
        config = new AppConfig(directory);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
    }

    private void recordAnswers(String topic, int correct, int wrong, int pending) {
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
        facade.submitAttempt(course.getId(), attempt.getId(), answers);
    }

    @Test
    public void ranksByAccuracyAndUsesTopicNamesToBreakTies() {
        recordAnswers("beta", 1, 4, 0);
        recordAnswers("Alpha", 1, 4, 0);
        recordAnswers("Strong", 4, 1, 0);
        llm.reply("Review Alpha and beta first; Strong has higher accuracy.");
        WeakTopicReport report = facade.getWeakTopics(course.getId());
        assertEquals("Alpha", report.getRankedTopics().get(0).getConceptName());
        assertEquals("beta", report.getRankedTopics().get(1).getConceptName());
        assertEquals("Strong", report.getRankedTopics().get(2).getConceptName());
        assertEquals(5, report.getRankedTopics().get(0).getGradedCount());
        assertTrue(report.toString().contains("20% (1/5 correct)"));
        assertTrue(report.getExplanation().contains("AI explanation:"));
        assertEquals(1, llm.getPrompts().size());
        assertTrue(llm.getPrompts().get(0).contains("Alpha: 1/5 correct"));
    }

    @Test
    public void listsSmallSamplesAsInsufficientInsteadOfRankingThem() {
        recordAnswers("Observer", 4, 0, 0);
        WeakTopicReport report = facade.getWeakTopics(course.getId());
        assertTrue(report.getRankedTopics().isEmpty());
        assertEquals(Arrays.asList("Observer"), report.getInsufficientConcepts());
        assertTrue(report.getExplanation().contains("at least 5"));
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void makesNoWeakestTopicClaimWhenThereIsNoHistory() {
        WeakTopicReport report = facade.getWeakTopics(course.getId());
        assertTrue(report.getRankedTopics().isEmpty());
        assertTrue(report.getInsufficientConcepts().isEmpty());
        assertTrue(report.getExplanation().contains("no weakest topic is identified"));
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void excludesPendingResultsFromTheMinimumSample() {
        recordAnswers("Observer", 4, 0, 3);
        WeakTopicReport report = facade.getWeakTopics(course.getId());
        assertTrue(report.getRankedTopics().isEmpty());
        assertTrue(report.getInsufficientConcepts().contains("Observer"));
        assertEquals(4, facade.getProgress(course.getId()).getGradedCount());
        assertEquals(3, llm.getPrompts().size());
    }

    @Test
    public void keepsTheRankingWhenTheExplanationServiceFails() {
        recordAnswers("Observer", 1, 4, 0);
        llm.failNext();
        WeakTopicReport report = facade.getWeakTopics(course.getId());
        assertEquals(1, report.getRankedTopics().size());
        assertEquals(0.2, report.getRankedTopics().get(0).getAccuracy(), 0.0001);
        assertTrue(report.getExplanation().contains("unavailable"));
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void treatsAnEmptyExplanationAsUnavailableWithoutRetrying() {
        recordAnswers("Observer", 5, 0, 0);
        llm.reply(" ");
        llm.reply("Unused test reply.");
        WeakTopicReport report = facade.getWeakTopics(course.getId());
        assertTrue(report.getExplanation().contains("unavailable"));
        assertEquals(1, report.getRankedTopics().size());
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void keepsAnotherCoursesStatisticsOutOfTheReport() {
        recordAnswers("Observer", 5, 0, 0);
        Course other = facade.createCourse("Other");
        WeakTopicReport report = facade.getWeakTopics(other.getId());
        assertTrue(report.getRankedTopics().isEmpty());
        assertTrue(report.getInsufficientConcepts().isEmpty());
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void appliesTheConfiguredMinimumAfterRestarting() throws Exception {
        recordAnswers("Observer", 1, 1, 0);
        Files.writeString(directory.resolve(".env"), "WEAK_TOPIC_MIN_RESULTS=2\n");
        AgentFacade restarted = new AgentFacade(new AppConfig(directory), llm);
        llm.reply("Review the Observer notes again.");
        assertEquals(1, restarted.getWeakTopics(course.getId()).getRankedTopics().size());
    }

    @Test
    public void keepsTheReportSnapshotWhenNewAnswersAreGraded() {
        recordAnswers("Observer", 5, 0, 0);
        llm.reply("The recorded answers are correct.");
        WeakTopicReport report = facade.getWeakTopics(course.getId());
        recordAnswers("Observer", 0, 1, 0);
        assertEquals(5, report.getRankedTopics().get(0).getGradedCount());
        assertEquals(6, facade.getProgress(course.getId()).getGradedCount());
    }

    @Test
    public void readsTheRankingWithoutChangingProgressOrHistory() throws Exception {
        recordAnswers("Observer", 4, 1, 0);
        Path data = config.getDataDirectory();
        String progress = Files.readString(data.resolve("progress.json"));
        String history = Files.readString(data.resolve("history.json"));
        llm.reply("Keep reviewing the topic.");
        facade.getWeakTopics(course.getId());
        assertEquals(progress, Files.readString(data.resolve("progress.json")));
        assertEquals(history, Files.readString(data.resolve("history.json")));
    }

    @Test
    public void doesNotRankFlashcardMasteryAsQuizPerformance() {
        Flashcard card = new Flashcard("What is Observer?", "A subject notifies subscribers.", "Observer");
        course.setFlashcards(Arrays.asList(card));
        new CourseRepository(config.getDataDirectory()).saveCourse(course);
        for (int i = 0; i < 5; i++) {
            facade.reviewCard(course.getId(), card.getId(), false);
        }
        WeakTopicReport report = facade.getWeakTopics(course.getId());
        assertEquals(5, facade.getProgress(course.getId()).getReviewsDone());
        assertTrue(report.getRankedTopics().isEmpty());
        assertEquals(0, llm.getPrompts().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnUnknownCourseBeforeCallingTheModel() {
        facade.getWeakTopics("missing");
    }
}
