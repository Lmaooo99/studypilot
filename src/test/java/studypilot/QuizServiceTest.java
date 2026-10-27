package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuizServiceTest {
    private AppConfig config;
    private MockLLMClient llm;
    private AgentFacade facade;
    private Course course;
    private final List<QuestionType> allTypes = Arrays.asList(QuestionType.MULTIPLE_CHOICE,
            QuestionType.TRUE_FALSE, QuestionType.SHORT_ANSWER);
    private final String choice = "{\"type\":\"MULTIPLE_CHOICE\",\"text\":\"Who receives Observer updates?\","
            + "\"options\":[\"Subscribers\",\"No one\"],\"correctAnswer\":\"Subscribers\",\"conceptName\":\"Observer\"}";
    private final String trueFalse = "{\"type\":\"TRUE_FALSE\",\"text\":\"Observer notifies subscribers.\","
            + "\"correctAnswer\":\"true\",\"conceptName\":\"Observer\"}";
    private final String shortAnswer = "{\"type\":\"SHORT_ANSWER\",\"text\":\"Explain Observer notification.\","
            + "\"correctAnswer\":\"The subject notifies subscribers.\",\"rubric\":\"Mention subject and subscribers.\",\"conceptName\":\"Observer\"}";

    @Before
    public void setUp() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-quiz-test-");
        config = new AppConfig(directory);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        Path notes = directory.resolve("notes.txt");
        Files.writeString(notes, "Observer allows a subject to notify its subscribers. A facade gives one entry point.");
        facade.importMaterial(course.getId(), notes.toString());
    }

    @Test
    public void generatesAllThreeTypesAndRestoresTheirOriginalIds() {
        llm.reply("{\"questions\":[" + choice + "," + trueFalse + "," + shortAnswer + "]}");
        Quiz quiz = facade.createQuiz(course.getId(), "Observer", 3, Difficulty.MEDIUM, allTypes);
        AgentFacade restarted = new AgentFacade(config, new MockLLMClient());
        Quiz loaded = restarted.getQuiz(course.getId(), quiz.getId());
        assertEquals(3, loaded.getQuestions().size());
        for (int i = 0; i < 3; i++) {
            assertEquals(allTypes.get(i), loaded.getQuestions().get(i).getType());
            assertEquals(quiz.getQuestions().get(i).getId(), loaded.getQuestions().get(i).getId());
        }
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void keepsValidDraftsAndRequestsOnlyMissingTypes() {
        String invalid = trueFalse.replace("\"true\"", "\"maybe\"");
        llm.reply("{\"questions\":[" + choice + "," + choice + "," + invalid + ",{\"type\":\"ESSAY\"}]}");
        llm.reply("{\"questions\":[" + trueFalse + "," + shortAnswer + "]}");
        Quiz quiz = facade.createQuiz(course.getId(), "Observer", 3, Difficulty.HARD, allTypes);
        assertEquals(3, quiz.getQuestions().size());
        assertEquals(2, llm.getPrompts().size());
        assertTrue(llm.getPrompts().get(1).contains("Create 2 distinct quiz questions"));
        assertTrue(llm.getPrompts().get(1).contains("Allowed types: [TRUE_FALSE, SHORT_ANSWER]"));
        assertTrue(llm.getPrompts().get(0).contains("HARD difficulty"));
        assertEquals(Difficulty.HARD, quiz.getDifficulty());
    }

    @Test
    public void returnsAFewerQuestionQuizAfterTheReplacementLimit() {
        llm.reply("{\"questions\":[" + choice + "]}");
        llm.reply("{\"questions\":[" + trueFalse + "]}");
        llm.reply("{\"questions\":[" + trueFalse + "]}");
        Quiz quiz = facade.createQuiz(course.getId(), "Observer", 3, Difficulty.MEDIUM, allTypes);
        assertEquals(2, quiz.getQuestions().size());
        assertEquals(3, llm.getPrompts().size());
        assertEquals(2, facade.getQuiz(course.getId(), quiz.getId()).getQuestions().size());
    }

    @Test
    public void savesNothingWhenEveryModelReplyIsUnusable() {
        llm.reply("not JSON");
        llm.reply("{\"questions\":[null]}");
        llm.failNext();
        try {
            facade.createQuiz(course.getId(), "Observer", 3, Difficulty.MEDIUM, allTypes);
            fail("An unusable quiz must not be saved.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No quiz was saved"));
        }
        assertEquals(0, facade.listQuizzes(course.getId()).size());
        assertEquals(3, llm.getPrompts().size());
    }

    @Test
    public void rejectsUnrelatedTopicsWithoutCallingTheModel() {
        try {
            facade.createQuiz(course.getId(), "volcanoes", 3, Difficulty.MEDIUM, allTypes);
            fail("A quiz needs relevant source material.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No relevant material"));
        }
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void rejectsTooFewQuestionsForTheSelectedTypesBeforeCallingTheModel() {
        try {
            facade.createQuiz(course.getId(), "Observer", 1, Difficulty.MEDIUM, allTypes);
            fail("Each selected type needs at least one question.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("each selected type"));
        }
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void discardsUnselectedTypesAndDoesNotExposeAnswersInTheDescription() {
        llm.reply("{\"questions\":[" + choice + "," + shortAnswer + "]}");
        Quiz quiz = facade.createQuiz(course.getId(), "Observer", 1, Difficulty.EASY,
                Arrays.asList(QuestionType.SHORT_ANSWER));
        assertEquals(1, quiz.getQuestions().size());
        assertEquals(QuestionType.SHORT_ANSWER, quiz.getQuestions().get(0).getType());
        assertTrue(quiz.describe().contains("Explain Observer notification."));
        assertFalse(quiz.describe().contains("The subject notifies subscribers."));
        assertFalse(quiz.describe().contains("Mention subject and subscribers."));
    }

    @Test
    public void preventsReadingAnotherCoursesQuiz() {
        llm.reply("{\"questions\":[" + trueFalse + "]}");
        Quiz quiz = facade.createQuiz(course.getId(), "Observer", 1, Difficulty.MEDIUM,
                Arrays.asList(QuestionType.TRUE_FALSE));
        Course other = facade.createCourse("Other course");
        assertEquals(0, facade.listQuizzes(other.getId()).size());
        try {
            facade.getQuiz(other.getId(), quiz.getId());
            fail("Quiz access must stay in its course.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("this course"));
        }
    }
}
