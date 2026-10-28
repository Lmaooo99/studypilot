package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DifficultyCLITest {
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private CLI cli;
    private ByteArrayOutputStream output;
    private ByteArrayOutputStream errors;

    @Before
    public void setUp() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-difficulty-cli-");
        AppConfig config = new AppConfig(directory);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        Path notes = directory.resolve("notes.txt");
        Files.writeString(notes, "Observer lets a subject notify subscribers.");
        facade.importMaterial(course.getId(), notes.toString());
        List<Question> questions = new ArrayList<>();
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            Question question = new TrueFalseQuestion("Observer statement " + i, "Observer", true);
            questions.add(question);
            answers.put(question.getId(), "true");
        }
        Quiz quiz = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        facade.submitAttempt(course.getId(), attempt.getId(), answers);
        output = new ByteArrayOutputStream();
        errors = new ByteArrayOutputStream();
        cli = new CLI(facade, new PrintStream(output), new PrintStream(errors));
    }

    private int generate(String override) {
        llm.reply("{\"questions\":[{\"type\":\"TRUE_FALSE\",\"text\":\"Observer notifies subscribers.\","
                + "\"correctAnswer\":\"true\",\"conceptName\":\"Observer\"}]}");
        List<String> arguments = new ArrayList<>();
        String[] required = {"quiz", "create", "--course", course.getId(), "--topic", "Observer",
                "--count", "1", "--types", "true-false"};
        for (String argument : required) {
            arguments.add(argument);
        }
        if (override != null) {
            arguments.add("--difficulty");
            arguments.add(override);
        }
        return cli.runCommand(arguments.toArray(new String[0]));
    }

    @Test
    public void showsTheSuggestionWithoutCallingTheModel() {
        assertEquals(0, cli.runCommand(new String[] {"quiz", "difficulty", "--course", course.getId(), "--topic", "Observer"}));
        assertTrue(output.toString().contains("Suggested difficulty: HARD"));
        assertTrue(output.toString().contains("100% over 5 graded answers"));
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void usesTheSuggestionForGenerationWhenNoOverrideIsGiven() {
        assertEquals(0, generate(null));
        assertTrue(output.toString().contains("Selected difficulty: HARD"));
        assertTrue(llm.getPrompts().get(0).contains("HARD difficulty"));
        assertEquals(2, facade.listQuizzes(course.getId()).size());
    }

    @Test
    public void usesAValidOverrideInsteadOfTheSuggestedLevel() {
        assertEquals(0, generate("easy"));
        assertTrue(output.toString().contains("Suggested difficulty: HARD"));
        assertTrue(output.toString().contains("Selected difficulty: EASY"));
        assertTrue(llm.getPrompts().get(0).contains("EASY difficulty"));
    }

    @Test
    public void warnsAndUsesTheSuggestionForAnInvalidOverride() {
        assertEquals(0, generate("impossible"));
        assertTrue(output.toString().contains("Invalid difficulty override; using the suggestion."));
        assertTrue(llm.getPrompts().get(0).contains("HARD difficulty"));
    }

    @Test
    public void reportsMissingSuggestionArgumentsWithoutAModelCall() {
        assertEquals(2, cli.runCommand(new String[] {"quiz", "difficulty", "--course", course.getId()}));
        assertTrue(errors.toString().contains("--topic"));
        assertEquals(0, llm.getPrompts().size());
    }
}
