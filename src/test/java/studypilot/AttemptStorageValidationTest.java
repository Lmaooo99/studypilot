package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AttemptStorageValidationTest {
    private AppConfig config;
    private Course course;
    private QuizAttempt attempt;
    private Path saved;
    private String original;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-saved-grade-fields-"));
        MockLLMClient llm = new MockLLMClient();
        AgentFacade facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        Question partial = new ShortAnswerQuestion(UUID.randomUUID().toString(), "Explain Observer.",
                "Observer", 2, "The subject notifies subscribers.", "Mention the subject and subscribers.");
        Question pending = new ShortAnswerQuestion("Explain notifications.", "Observer", "Subscribers receive updates.", "Mention updates.");
        Quiz quiz = new Quiz(course.getId(), "Observer", Difficulty.MEDIUM, Arrays.asList(partial, pending));
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        Map<String, String> answers = new HashMap<>();
        answers.put(partial.getId(), "Notifications.");
        answers.put(pending.getId(), "Subscribers receive updates.");
        llm.reply("{\"pointsEarned\":1,\"explanation\":\"Partial credit.\"}");
        llm.failNext();
        QuizAttempt started = facade.startAttempt(course.getId(), quiz.getId());
        attempt = facade.submitAttempt(course.getId(), started.getId(), answers);
        saved = config.getDataDirectory().resolve("attempts").resolve(attempt.getId() + ".json");
        original = Files.readString(saved);
    }

    private void rejectsWithoutChangingTheFile(JSONObject data) throws Exception {
        String damaged = data.toString();
        Files.writeString(saved, damaged);
        try {
            new AgentFacade(config, new MockLLMClient()).getAttempt(course.getId(), attempt.getId());
            fail("Invalid saved grades must not be silently converted or repaired.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("original file was not changed"));
        }
        assertEquals(damaged, Files.readString(saved));
    }

    @Test
    public void rejectsFractionalAndStringSavedGrades() throws Exception {
        for (Object value : new Object[] {1.5, "1"}) {
            JSONObject data = new JSONObject(original);
            data.getJSONArray("results").getJSONObject(0).put("pointsEarned", value);
            rejectsWithoutChangingTheFile(data);
        }
    }

    @Test
    public void rejectsFractionalAndStringMaximumPoints() throws Exception {
        for (Object value : new Object[] {2.5, "2"}) {
            JSONObject data = new JSONObject(original);
            data.getJSONArray("results").getJSONObject(0).put("maxPoints", value);
            rejectsWithoutChangingTheFile(data);
        }
    }

    @Test
    public void rejectsStringGradingFlags() throws Exception {
        String[] fields = {"correct", "counted"};
        String[] values = {"false", "true"};
        for (int i = 0; i < fields.length; i++) {
            JSONObject data = new JSONObject(original);
            data.getJSONArray("results").getJSONObject(0).put(fields[i], values[i]);
            rejectsWithoutChangingTheFile(data);
        }
    }

    @Test
    public void rejectsACountedPendingAnswer() throws Exception {
        JSONObject data = new JSONObject(original);
        data.getJSONArray("results").getJSONObject(1).put("counted", true);
        rejectsWithoutChangingTheFile(data);
    }

    @Test
    public void rejectsATopicThatDoesNotMatchTheSavedQuestion() throws Exception {
        JSONObject data = new JSONObject(original);
        data.getJSONArray("results").getJSONObject(0).put("conceptName", "Different topic");
        rejectsWithoutChangingTheFile(data);
    }
}
