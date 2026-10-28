package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeakTopicCLITest {
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private CLI cli;
    private ByteArrayOutputStream output;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-weak-cli-"));
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        output = new ByteArrayOutputStream();
        cli = new CLI(facade, new PrintStream(output), new PrintStream(new ByteArrayOutputStream()));
    }

    @Test
    public void reportsInsufficientHistoryWithoutAnApiCall() {
        assertEquals(0, cli.runCommand(new String[] {"weak-topics", "--course", course.getId()}));
        assertTrue(output.toString().contains("No topics have enough graded answers"));
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void requiresACourseAndRejectsUnrelatedFlags() {
        assertEquals(2, cli.runCommand(new String[] {"weak-topics"}));
        assertEquals(2, cli.runCommand(new String[] {"weak-topics", "--course", course.getId(), "--unknown", "value"}));
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void displaysTheRankingEvenWhenTheAiExplanationFails() {
        List<Question> questions = new ArrayList<>();
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            Question question = new TrueFalseQuestion("Observer statement " + i, "Observer", true);
            questions.add(question);
            answers.put(question.getId(), "false");
        }
        Quiz quiz = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        facade.submitAttempt(course.getId(), attempt.getId(), answers);
        llm.failNext();
        assertEquals(0, cli.runCommand(new String[] {"weak-topics", "--course", course.getId()}));
        assertTrue(output.toString().contains("Observer - 0% (0/5 correct)"));
        assertTrue(output.toString().contains("AI explanation unavailable"));
    }
}
