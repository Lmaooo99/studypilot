package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuizRepositoryTest {
    private Path directory;
    private QuizRepository repository;
    private Quiz quiz;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("studypilot-quiz-storage-test-");
        repository = new QuizRepository(directory);
        List<Question> questions = new ArrayList<>();
        questions.add(new MultipleChoiceQuestion("Choose one.", "Design", Arrays.asList("Facade", "Observer"), 1));
        questions.add(new TrueFalseQuestion("A facade is an observer.", "Design", false));
        questions.add(new ShortAnswerQuestion("Explain a facade.", "Design", "One entry point.", "Mention one entry point."));
        quiz = new Quiz(UUID.randomUUID().toString(), "Design", Difficulty.HARD, questions);
    }

    @Test
    public void preservesTypesIdsOptionsAnswersAndRubricsWhenReloaded() {
        repository.saveQuiz(quiz);
        Quiz loaded = new QuizRepository(directory).loadQuiz(quiz.getId());
        assertEquals(quiz.getCourseId(), loaded.getCourseId());
        assertEquals(Difficulty.HARD, loaded.getDifficulty());
        for (int i = 0; i < 3; i++) {
            Question before = quiz.getQuestions().get(i);
            Question after = loaded.getQuestions().get(i);
            assertEquals(before.getId(), after.getId());
            assertEquals(before.getType(), after.getType());
            assertEquals(before.getCorrectAnswer(), after.getCorrectAnswer());
            assertEquals(before.getPoints(), after.getPoints());
        }
        assertEquals(1, ((MultipleChoiceQuestion) loaded.getQuestions().get(0)).getCorrectIndex());
        assertEquals("Mention one entry point.", ((ShortAnswerQuestion) loaded.getQuestions().get(2)).getRubric());
    }

    @Test
    public void keepsSavedContentWhenReturnedListsAreChangedOrOverwriteIsAttempted() throws Exception {
        repository.saveQuiz(quiz);
        Path file = directory.resolve("quizzes").resolve(quiz.getId() + ".json");
        String original = Files.readString(file);
        quiz.getQuestions().clear();
        ((MultipleChoiceQuestion) quiz.getQuestions().get(0)).getOptions().clear();
        assertEquals(3, quiz.getQuestions().size());
        assertEquals(2, ((MultipleChoiceQuestion) quiz.getQuestions().get(0)).getOptions().size());
        try {
            repository.saveQuiz(quiz);
            fail("A stored quiz must not be overwritten.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("cannot be overwritten"));
        }
        assertEquals(original, Files.readString(file));
    }

    @Test
    public void rejectsAnUnknownSavedQuestionTypeWithoutChangingTheFile() throws Exception {
        repository.saveQuiz(quiz);
        Path file = directory.resolve("quizzes").resolve(quiz.getId() + ".json");
        JSONObject data = new JSONObject(Files.readString(file));
        data.getJSONArray("questions").getJSONObject(0).put("type", "UNKNOWN");
        String damaged = data.toString();
        Files.writeString(file, damaged);
        try {
            repository.loadQuiz(quiz.getId());
            fail("An unknown type must not be changed into a different question type.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("original file was not changed"));
        }
        assertEquals(damaged, Files.readString(file));
    }
}
