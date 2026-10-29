package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgentToolsTest {
    private AppConfig config;
    private AgentFacade facade;
    private MockLLMClient llm;
    private Course course;
    private Material material;

    @Before
    public void setUp() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-tools-test-");
        config = new AppConfig(directory);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Design");
        Path file = directory.resolve("notes.md");
        Files.writeString(file, "Observer lets a subject notify its subscribers.");
        material = facade.importMaterial(course.getId(), file.toString());
    }

    private void plan(String tool, JSONObject arguments) {
        JSONObject step = new JSONObject().put("stepId", "step1").put("toolName", tool).put("arguments", arguments);
        llm.reply(new JSONObject().put("steps", new JSONArray().put(step)).toString());
    }

    @Test
    public void summaryToolCallsTheExistingSummaryServiceAndSavesItsResult() {
        plan("summarize", new JSONObject().put("materialId", material.getId()).put("style", "bullet"));
        llm.reply("- A subject notifies subscribers.");
        AgentResult result = facade.runAgent(course.getId(), null, "Summarize the notes.");
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals("- A subject notifies subscribers.", facade.getCourse(course.getId()).getMaterial(material.getId()).getSummary());
        assertEquals(material.getId(), result.getResults().get("step1").getData().get("materialId"));
    }

    @Test
    public void answerToolUsesRetrievedAndCheckedCitations() {
        plan("answer", new JSONObject().put("question", "How does Observer notify subscribers?"));
        String chunkId = material.getChunks().get(0).getId();
        llm.reply(new JSONObject().put("answer", "The subject notifies subscribers.")
                .put("citedChunkIds", new JSONArray().put(chunkId)).toString());
        AgentResult result = facade.runAgent(course.getId(), null, "Explain Observer.");
        assertEquals(ResultStatus.OK, result.getStatus());
        assertTrue(result.getFinalAnswer().contains("notes.md"));
        assertTrue(result.getFinalAnswer().contains("Sources (retrieved chunk IDs checked)"));
    }

    @Test
    public void quizToolCreatesARealSavedTypedQuiz() {
        plan("quiz", new JSONObject().put("topic", "Observer").put("count", "1").put("types", "true-false"));
        JSONObject question = new JSONObject().put("type", "TRUE_FALSE").put("text", "A subject notifies subscribers.")
                .put("correctAnswer", "true").put("conceptName", "Observer");
        llm.reply(new JSONObject().put("questions", new JSONArray().put(question)).toString());
        AgentResult result = facade.runAgent(course.getId(), null, "Create one true/false question.");
        assertEquals(ResultStatus.OK, result.getStatus());
        String quizId = result.getResults().get("step1").getData().get("quizId");
        assertEquals(QuestionType.TRUE_FALSE, facade.getQuiz(course.getId(), quizId).getQuestions().get(0).getType());
        assertEquals(1, facade.listQuizzes(course.getId()).size());
    }

    @Test
    public void weakTopicToolReturnsTheActualGradedRanking() {
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
        plan("weakTopics", new JSONObject());
        llm.reply("Review the subject notification flow.");
        AgentResult result = facade.runAgent(course.getId(), null, "Show weak topics.");
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals("Observer", result.getResults().get("step1").getData().get("topic"));
        assertEquals("5", result.getResults().get("step1").getData().get("gradedCount"));
        assertTrue(result.getFinalAnswer().contains("0/5 correct"));
    }

    @Test
    public void studyPlanToolUsesTheExistingPlanValidationAndRepository() {
        LocalDate today = LocalDate.now();
        plan("studyPlan", new JSONObject().put("examDate", today.plusDays(1).toString()).put("hoursPerDay", "1"));
        JSONObject task = new JSONObject().put("date", today.toString()).put("type", "DIAGNOSTIC_QUIZ")
                .put("conceptName", "Observer").put("description", "Take a diagnostic quiz.").put("minutes", 15);
        llm.reply(new JSONObject().put("tasks", new JSONArray().put(task)).toString());
        AgentResult result = facade.runAgent(course.getId(), null, "Create a study plan.");
        assertEquals(ResultStatus.OK, result.getStatus());
        String planId = result.getResults().get("step1").getData().get("planId");
        assertEquals(TaskType.DIAGNOSTIC_QUIZ, facade.getStudyPlan(course.getId(), planId).getTasks().get(0).getType());
    }

    @Test
    public void progressToolReadsActualReviewCountsWithoutAnExtraModelCall() {
        Flashcard card = new Flashcard("Who notifies?", "The subject.", "Observer");
        course = facade.getCourse(course.getId());
        course.setFlashcards(java.util.Arrays.asList(card));
        new CourseRepository(config.getDataDirectory()).saveCourse(course);
        facade.reviewCard(course.getId(), card.getId(), true);
        plan("progress", new JSONObject());
        AgentResult result = facade.runAgent(course.getId(), null, "Show progress.");
        assertEquals("1", result.getResults().get("step1").getData().get("reviewsDone"));
        assertTrue(result.getFinalAnswer().contains("Reviews completed: 1"));
        assertEquals(1, llm.getPrompts().size());
    }

    @Test
    public void summaryToolCannotReadAMaterialFromAnotherCourse() throws Exception {
        Course other = facade.createCourse("Other");
        Path file = config.getDataDirectory().resolveSibling("other.md");
        Files.writeString(file, "Other course information.");
        Material foreign = facade.importMaterial(other.getId(), file.toString());
        plan("summarize", new JSONObject().put("materialId", foreign.getId()));
        AgentResult result = facade.runAgent(course.getId(), null, "Summarize notes.");
        assertEquals(ResultStatus.FAILED, result.getStatus());
        assertFalse(result.getFinalAnswer().contains("Other course information"));
        assertEquals(1, llm.getPrompts().size());
    }
}
