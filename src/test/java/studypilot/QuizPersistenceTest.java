package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuizPersistenceTest {
    private Path workspace;
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private Quiz quiz;
    private Path saved;
    private String original;
    private final List<QuestionType> types = Arrays.asList(QuestionType.MULTIPLE_CHOICE,
            QuestionType.TRUE_FALSE, QuestionType.SHORT_ANSWER);
    private final String proposal = "{\"questions\":["
            + "{\"type\":\"MULTIPLE_CHOICE\",\"text\":\"Who receives Observer updates?\",\"options\":[\"No one\",\"Subscribers\"],\"correctAnswer\":\"Subscribers\",\"conceptName\":\"Observer\"},"
            + "{\"type\":\"TRUE_FALSE\",\"text\":\"Observer prevents all notifications.\",\"correctAnswer\":\"false\",\"conceptName\":\"Observer\"},"
            + "{\"type\":\"SHORT_ANSWER\",\"text\":\"Explain Observer notifications.\",\"correctAnswer\":\"The subject notifies subscribers.\",\"rubric\":\"Mention the subject and subscribers.\",\"conceptName\":\"Observer\"}]}";

    @Before
    public void setUp() throws Exception {
        workspace = Files.createTempDirectory("studypilot-quiz-persistence-");
        config = new AppConfig(workspace);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        Path notes = workspace.resolve("observer.txt");
        Files.writeString(notes, "Observer lets a subject notify subscribers.");
        facade.importMaterial(course.getId(), notes.toString());
        llm.reply(proposal);
        quiz = facade.createQuiz(course.getId(), "Observer", 3, Difficulty.HARD, types);
        saved = config.getDataDirectory().resolve("quizzes").resolve(quiz.getId() + ".json");
        original = Files.readString(saved);
    }

    private void assertQuestionsUnchanged(Quiz restored) {
        assertEquals(quiz.getId(), restored.getId());
        assertEquals(quiz.getCourseId(), restored.getCourseId());
        assertEquals(Difficulty.HARD, restored.getDifficulty());
        assertEquals(3, restored.getQuestions().size());
        for (int i = 0; i < types.size(); i++) {
            Question before = quiz.getQuestions().get(i);
            Question after = restored.getQuestions().get(i);
            assertEquals(before.getId(), after.getId());
            assertEquals(types.get(i), after.getType());
            assertEquals(before.getText(), after.getText());
            assertEquals(before.getConceptName(), after.getConceptName());
            assertEquals(before.getCorrectAnswer(), after.getCorrectAnswer());
            assertEquals(before.getPoints(), after.getPoints());
        }
        MultipleChoiceQuestion choice = (MultipleChoiceQuestion) restored.getQuestions().get(0);
        assertEquals(Arrays.asList("No one", "Subscribers"), choice.getOptions());
        assertEquals(1, choice.getCorrectIndex());
        assertEquals("Mention the subject and subscribers.", ((ShortAnswerQuestion) restored.getQuestions().get(2)).getRubric());
    }

    @Test
    public void laterImportsAndQuizGenerationDoNotChangeAnEarlierMixedQuiz() throws Exception {
        Path additional = workspace.resolve("facade.md");
        Files.writeString(additional, "Facade provides one entry point.");
        facade.importMaterial(course.getId(), additional.toString());
        llm.reply(proposal);
        Quiz next = new AgentFacade(config, llm).createQuiz(course.getId(), "Observer", 3, Difficulty.EASY, types);
        MockLLMClient reader = new MockLLMClient();
        AgentFacade restarted = new AgentFacade(config, reader);
        assertQuestionsUnchanged(restarted.getQuiz(course.getId(), quiz.getId()));
        assertFalse(quiz.getId().equals(next.getId()));
        assertFalse(quiz.getQuestions().get(0).getId().equals(next.getQuestions().get(0).getId()));
        assertEquals(2, restarted.listQuizzes(course.getId()).size());
        assertEquals(original, Files.readString(saved));
        assertEquals(0, reader.getPrompts().size());
    }

    @Test
    public void restoredQuestionIdsAcceptAnswersWithoutOriginalFilesOrAModelCall() throws Exception {
        Files.delete(workspace.resolve("observer.txt"));
        MockLLMClient reader = new MockLLMClient();
        AgentFacade restarted = new AgentFacade(config, reader);
        Quiz restored = restarted.getQuiz(course.getId(), quiz.getId());
        QuizAttempt attempt = restarted.startAttempt(course.getId(), restored.getId());
        Map<String, String> answers = new HashMap<>();
        answers.put(quiz.getQuestions().get(0).getId(), "2");
        answers.put(quiz.getQuestions().get(1).getId(), "false");
        answers.put(quiz.getQuestions().get(2).getId(), "");
        restarted.submitAttempt(course.getId(), attempt.getId(), answers);
        AgentFacade reopened = new AgentFacade(config, reader);
        QuizAttempt submitted = reopened.getAttempt(course.getId(), attempt.getId());
        assertEquals(answers, submitted.getAnswers());
        assertEquals(2, submitted.getPointsEarned());
        assertEquals(3, submitted.getGradedPoints());
        assertEquals(0, submitted.getPendingCount());
        for (Question question : quiz.getQuestions()) {
            assertEquals(question.getId(), submitted.getResult(question.getId()).getQuestionId());
        }
        assertQuestionsUnchanged(reopened.getQuiz(course.getId(), quiz.getId()));
        assertEquals(original, Files.readString(saved));
        assertEquals(0, reader.getPrompts().size());
    }

    @Test
    public void quizListsAndAttemptAccessRetainTheirCourseScopeAfterRestart() throws Exception {
        Course other = facade.createCourse("Other course");
        facade.importMaterial(other.getId(), workspace.resolve("observer.txt").toString());
        llm.reply(proposal);
        Quiz otherQuiz = facade.createQuiz(other.getId(), "Observer", 3, Difficulty.MEDIUM, types);
        MockLLMClient reader = new MockLLMClient();
        AgentFacade restarted = new AgentFacade(config, reader);
        assertEquals(quiz.getId(), restarted.listQuizzes(course.getId()).get(0).getId());
        assertEquals(otherQuiz.getId(), restarted.listQuizzes(other.getId()).get(0).getId());
        try {
            restarted.startAttempt(other.getId(), quiz.getId());
            fail("The saved quiz must remain in its original course.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("this course"));
        }
        assertTrue(restarted.listAttempts(other.getId()).isEmpty());
        assertEquals(original, Files.readString(saved));
        assertEquals(0, reader.getPrompts().size());
    }

    private void rejectsWithoutChangingTheFile(JSONObject data) throws Exception {
        String damaged = data.toString();
        Files.writeString(saved, damaged);
        try {
            new AgentFacade(config, new MockLLMClient()).getQuiz(course.getId(), quiz.getId());
            fail("Invalid saved question fields must not be silently converted.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("original file was not changed"));
        }
        assertEquals(damaged, Files.readString(saved));
    }

    @Test
    public void rejectsFractionalAndStringSavedQuestionPoints() throws Exception {
        for (Object value : new Object[] {1.5, "1"}) {
            JSONObject data = new JSONObject(original);
            data.getJSONArray("questions").getJSONObject(0).put("points", value);
            rejectsWithoutChangingTheFile(data);
        }
    }

    @Test
    public void rejectsFractionalAndStringSavedCorrectIndices() throws Exception {
        for (Object value : new Object[] {1.5, "1"}) {
            JSONObject data = new JSONObject(original);
            data.getJSONArray("questions").getJSONObject(0).put("correctIndex", value);
            rejectsWithoutChangingTheFile(data);
        }
    }

    @Test
    public void rejectsDuplicateQuestionIdsWithoutReplacingTheSavedQuiz() throws Exception {
        JSONObject data = new JSONObject(original);
        String id = data.getJSONArray("questions").getJSONObject(0).getString("questionId");
        data.getJSONArray("questions").getJSONObject(1).put("questionId", id);
        rejectsWithoutChangingTheFile(data);
    }

    @Test
    public void aLaterGenerationFailureRetainsTheEarlierQuizAndCourseFile() throws Exception {
        Path courseFile = config.getDataDirectory().resolve("courses").resolve(course.getId() + ".json");
        String before = Files.readString(courseFile);
        MockLLMClient failing = new MockLLMClient();
        failing.failNext();
        failing.failNext();
        failing.failNext();
        AgentFacade restarted = new AgentFacade(config, failing);
        try {
            restarted.createQuiz(course.getId(), "Observer", 3, Difficulty.MEDIUM, types);
            fail("Three failed proposals must not replace an earlier quiz.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No quiz was saved"));
        }
        assertEquals(3, failing.getPrompts().size());
        assertEquals(1, restarted.listQuizzes(course.getId()).size());
        assertQuestionsUnchanged(restarted.getQuiz(course.getId(), quiz.getId()));
        assertEquals(original, Files.readString(saved));
        assertEquals(before, Files.readString(courseFile));
    }
}
