package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CLITest {
    private CLI cli;
    private ByteArrayOutputStream output;
    private ByteArrayOutputStream errors;
    private AgentFacade facade;
    private MockLLMClient llm;

    @Before
    public void setUp() throws Exception {
        output = new ByteArrayOutputStream();
        errors = new ByteArrayOutputStream();
        llm = new MockLLMClient();
        Path directory = Files.createTempDirectory("studypilot-cli-test-");
        AppConfig config = new AppConfig(directory);
        facade = new AgentFacade(config, llm);
        cli = new CLI(facade, new PrintStream(output), new PrintStream(errors));
    }

    @Test
    public void showsHelpWithoutAnApiKey() {
        assertEquals(0, cli.runCommand(new String[] {"help"}));
        assertTrue(output.toString().contains("gui"));
        assertEquals("", errors.toString());
    }

    @Test
    public void reportsAnUnknownCommandAsAnInputError() {
        assertEquals(2, cli.runCommand(new String[] {"missing-command"}));
        assertTrue(errors.toString().contains("Unknown command"));
    }

    @Test
    public void reportsMissingCommandArgumentsWithoutThrowingAnException() {
        String[] commands = {"course", "material", "summarize", "concepts", "ask", "flashcards", "review", "quiz"};
        for (String command : commands) {
            assertEquals("Incomplete command: " + command, 2, cli.runCommand(new String[] {command}));
        }
        assertTrue(errors.toString().contains("Unknown command"));
    }

    @Test
    public void createsACourseAndImportsMaterialThroughTheFacade() throws Exception {
        assertEquals(0, cli.runCommand(new String[] {"course", "add", "Design"}));
        String id = output.toString().split("\\R")[0].substring("Course ID: ".length());
        Path file = Files.createTempFile("studypilot-cli-notes-", ".md");
        Files.writeString(file, "A facade provides one entry point.");
        assertEquals(0, cli.runCommand(new String[] {"material", "import", file.toString(), "--course", id}));
        assertTrue(output.toString().contains("Chunks: 1"));
    }

    @Test
    public void requiresAnExplicitCourseForMaterialCommands() {
        assertEquals(2, cli.runCommand(new String[] {"material", "list"}));
        assertTrue(errors.toString().contains("Required option: --course"));
    }

    @Test
    public void generatesFlipsAndReviewsACardThroughCliCommands() throws Exception {
        Course course = facade.createCourse("CLI review");
        Path file = Files.createTempFile("studypilot-cli-card-", ".txt");
        Files.writeString(file, "A facade is a simple entry point.");
        Material material = facade.importMaterial(course.getId(), file.toString());
        llm.reply("{\"cards\":[{\"front\":\"What is a facade?\",\"back\":\"A simple entry point.\"}]}");
        assertEquals(0, cli.runCommand(new String[] {"flashcards", "generate", material.getId(), "--course", course.getId(), "--count", "1"}));
        String cardId = facade.getCardsToReview(course.getId()).get(0).getId();
        assertEquals(0, cli.runCommand(new String[] {"flashcards", "show", cardId, "--course", course.getId()}));
        assertTrue(output.toString().contains("Back: A simple entry point."));
        assertEquals(0, cli.runCommand(new String[] {"review", course.getId(), "--card", cardId, "--remembered", "true"}));
        assertEquals(0, cli.runCommand(new String[] {"progress", "--course", course.getId()}));
        assertTrue(output.toString().contains("Reviews completed: 1"));
        assertEquals(1, facade.getHistory(course.getId()).size());
    }

    @Test
    public void rejectsAnUnknownReviewValueWithoutRecordingProgress() {
        Course course = facade.createCourse("CLI review validation");
        assertEquals(2, cli.runCommand(new String[] {"review", course.getId(), "--card", "missing", "--remembered", "maybe"}));
        assertEquals(0, facade.getProgress(course.getId()).getReviewsDone());
    }

    @Test
    public void createsListsAndShowsAQuizUsingTheSelectedCourse() throws Exception {
        Course course = facade.createCourse("CLI quiz");
        Path file = Files.createTempFile("studypilot-cli-quiz-", ".txt");
        Files.writeString(file, "An observer receives notifications.");
        facade.importMaterial(course.getId(), file.toString());
        llm.reply("{\"questions\":[{\"type\":\"TRUE_FALSE\",\"text\":\"An observer receives notifications.\","
                + "\"correctAnswer\":\"true\",\"conceptName\":\"Observer\"}]}");
        assertEquals(0, cli.runCommand(new String[] {"quiz", "create", "--course", course.getId(),
                "--topic", "Observer", "--count", "1", "--types", "true-false", "--difficulty", "easy"}));
        assertTrue(output.toString().contains("Questions generated: 1. Requested: 1."));
        String id = facade.listQuizzes(course.getId()).get(0).getId();
        assertEquals(0, cli.runCommand(new String[] {"quiz", "list", "--course", course.getId()}));
        assertEquals(0, cli.runCommand(new String[] {"quiz", "show", id, "--course", course.getId()}));
        assertTrue(output.toString().contains(id));
        assertTrue(output.toString().contains("Difficulty: EASY"));
    }

    @Test
    public void rejectsUnknownQuizFlagsAndTypesBeforeCallingTheModel() {
        Course course = facade.createCourse("CLI quiz arguments");
        assertEquals(2, cli.runCommand(new String[] {"quiz", "create", "--course", course.getId(),
                "--topic", "Observer", "--types", "essay"}));
        assertEquals(2, cli.runCommand(new String[] {"quiz", "list", "--course", course.getId(), "--wrong", "value"}));
        assertEquals(0, llm.getPrompts().size());
    }

    private Quiz createObjectiveQuiz() throws Exception {
        Course course = facade.createCourse("CLI objective quiz");
        Path file = Files.createTempFile("studypilot-cli-objective-", ".txt");
        Files.writeString(file, "Observer is a pattern in which observers receive notifications.");
        facade.importMaterial(course.getId(), file.toString());
        llm.reply("{\"questions\":[{\"type\":\"TRUE_FALSE\",\"text\":\"Observers receive notifications.\","
                + "\"correctAnswer\":\"true\",\"conceptName\":\"Observer\"}]}");
        return facade.createQuiz(course.getId(), "Observer", 1, Difficulty.MEDIUM, Arrays.asList(QuestionType.TRUE_FALSE));
    }

    @Test
    public void takesSubmitsAndReadsTheSameAttemptThroughCliCommands() throws Exception {
        Quiz quiz = createObjectiveQuiz();
        String courseId = quiz.getCourseId();
        assertEquals(0, cli.runCommand(new String[] {"quiz", "take", quiz.getId(), "--course", courseId}));
        QuizAttempt attempt = facade.listAttempts(courseId).get(0);
        Path answers = Files.createTempFile("studypilot-cli-answers-", ".json");
        Files.writeString(answers, "{\"answers\":{\"" + quiz.getQuestions().get(0).getId() + "\":\"true\"}}");
        String[] submit = {"quiz", "submit", attempt.getId(), "--course", courseId, "--answers", answers.toString()};
        assertEquals(0, cli.runCommand(submit));
        assertEquals(0, cli.runCommand(submit));
        assertEquals(0, cli.runCommand(new String[] {"quiz", "attempt", attempt.getId(), "--course", courseId}));
        assertEquals(0, cli.runCommand(new String[] {"quiz", "attempts", "--course", courseId}));
        assertTrue(output.toString().contains("Score: 1/1 (100%)"));
        assertEquals(1, facade.getProgress(courseId).getQuizzesTaken());
        assertEquals(1, facade.getHistory(courseId).size());
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void rejectsANonTextAnswersFileWithoutSubmitting() throws Exception {
        Quiz quiz = createObjectiveQuiz();
        QuizAttempt attempt = facade.startAttempt(quiz.getCourseId(), quiz.getId());
        Path file = Files.createTempFile("studypilot-invalid-answers-", ".json");
        Files.writeString(file, "{\"answers\":{\"" + quiz.getQuestions().get(0).getId() + "\":true}}");
        assertEquals(2, cli.runCommand(new String[] {"quiz", "submit", attempt.getId(), "--course", quiz.getCourseId(),
                "--answers", file.toString()}));
        assertTrue(errors.toString().contains("answers object of text values"));
        assertFalse(facade.getAttempt(quiz.getCourseId(), attempt.getId()).isSubmitted());
    }

    private QuizAttempt createPendingCliAttempt() throws Exception {
        Course course = facade.createCourse("CLI pending answer");
        Path file = Files.createTempFile("studypilot-cli-pending-", ".txt");
        Files.writeString(file, "Observer lets a subject notify subscribers.");
        facade.importMaterial(course.getId(), file.toString());
        llm.reply("{\"questions\":[{\"type\":\"SHORT_ANSWER\",\"text\":\"Explain Observer.\","
                + "\"correctAnswer\":\"The subject notifies subscribers.\",\"rubric\":\"Mention notifications.\",\"conceptName\":\"Observer\"}]}");
        Quiz quiz = facade.createQuiz(course.getId(), "Observer", 1, Difficulty.MEDIUM, Arrays.asList(QuestionType.SHORT_ANSWER));
        QuizAttempt attempt = facade.startAttempt(course.getId(), quiz.getId());
        Map<String, String> answers = new HashMap<>();
        answers.put(quiz.getQuestions().get(0).getId(), "The subject notifies subscribers.");
        llm.failNext();
        return facade.submitAttempt(course.getId(), attempt.getId(), answers);
    }

    @Test
    public void previewsAndRetriesPendingGradingThroughTheCli() throws Exception {
        QuizAttempt attempt = createPendingCliAttempt();
        String course = attempt.getCourseId();
        String question = attempt.getResults().get(0).getQuestionId();
        assertEquals(0, cli.runCommand(new String[] {"quiz", "attempt", attempt.getId(), "--course", course}));
        assertTrue(output.toString().contains("Pending question IDs:"));
        assertTrue(output.toString().contains(question));
        assertEquals(0, cli.runCommand(new String[] {"quiz", "model-answer", attempt.getId(), question, "--course", course}));
        assertTrue(output.toString().contains("Model answer: The subject notifies subscribers."));
        llm.reply("{\"pointsEarned\":1,\"explanation\":\"Complete answer.\"}");
        String[] retry = {"quiz", "regrade", attempt.getId(), "--course", course};
        assertEquals(0, cli.runCommand(retry));
        assertEquals(0, cli.runCommand(retry));
        assertTrue(output.toString().contains("No pending answers to grade."));
        assertEquals(1, facade.getProgress(course).getGradedCount());
        assertEquals(3, llm.getPrompts().size());
    }

    @Test
    public void selfGradesOnlyPendingAnswersAndRequiresAnExplicitVerdict() throws Exception {
        QuizAttempt attempt = createPendingCliAttempt();
        String course = attempt.getCourseId();
        String question = attempt.getResults().get(0).getQuestionId();
        assertEquals(2, cli.runCommand(new String[] {"quiz", "selfgrade", attempt.getId(), question, "maybe", "--course", course}));
        assertEquals(0, cli.runCommand(new String[] {"quiz", "selfgrade", attempt.getId(), question, "right", "--course", course}));
        assertEquals(2, cli.runCommand(new String[] {"quiz", "selfgrade", attempt.getId(), question, "wrong", "--course", course}));
        assertEquals(1, facade.getProgress(course).getGradedCount());
        assertEquals(1, facade.getProgress(course).getCorrectCount());
        assertEquals(2, llm.getPrompts().size());
    }
}
