package studypilot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudyWorkflowRestartTest {
    private AppConfig config;
    private MockLLMClient llm;
    private Course course;
    private Material material;
    private String sourceId;
    private Flashcard card;
    private Quiz quiz;
    private QuizAttempt attempt;
    private StudyPlan plan;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-complete-study-"));
        llm = new MockLLMClient();
        course = new AgentFacade(config, llm).createCourse("Observer study workflow");
    }

    private void learnAndReview() throws Exception {
        AgentFacade learner = new AgentFacade(config, llm);
        Path notes = config.getDataDirectory().getParent().resolve("observer.md");
        Files.writeString(notes, "Observer lets a subject notify subscribers. Subscribers receive update notifications.");
        material = learner.importMaterial(course.getId(), notes.toString());
        sourceId = material.getChunks().get(0).getId();
        llm.reply("A subject notifies its subscribers when its state changes.");
        learner.summarize(course.getId(), material.getId(), "brief");
        JSONObject concept = new JSONObject().put("name", "Observer").put("definition", "A subject notifies subscribers.")
                .put("parentName", "").put("sourceChunkIds", new JSONArray().put(sourceId));
        llm.reply(new JSONObject().put("concepts", new JSONArray().put(concept)).toString());
        learner.extractConcepts(course.getId(), material.getId());
        llm.reply(new JSONObject().put("answer", "Subscribers receive notifications.")
                .put("citedChunkIds", new JSONArray().put(sourceId)).toString());
        Answer answer = learner.askQuestion(course.getId(), "Who receives Observer notifications?");
        assertEquals(sourceId, answer.getCitations().get(0).getChunkId());
        llm.reply("{\"cards\":[{\"front\":\"Who receives updates?\",\"back\":\"Subscribers.\",\"conceptName\":\"Observer\"}]}");
        card = learner.generateFlashcards(course.getId(), material.getId(), 1).get(0);
        learner.reviewCard(course.getId(), card.getId(), true);
    }

    private JSONObject draft(String type, String text, String answer) {
        return new JSONObject().put("type", type).put("text", text).put("correctAnswer", answer).put("conceptName", "Observer");
    }

    private void takeMixedQuiz() {
        JSONArray questions = new JSONArray();
        for (int i = 0; i < 2; i++) {
            questions.put(draft("MULTIPLE_CHOICE", "Who receives update " + i + "?", "Subscribers")
                    .put("options", new JSONArray().put("Subscribers").put("Unrelated clients")));
            questions.put(draft("TRUE_FALSE", "A subject notifies subscribers in case " + i + ".", "true"));
            questions.put(draft("SHORT_ANSWER", "Explain Observer notification " + i + ".", "The subject notifies subscribers.")
                    .put("rubric", "Mention the subject notifying subscribers."));
        }
        llm.reply(new JSONObject().put("questions", questions).toString());
        AgentFacade student = new AgentFacade(config, llm);
        quiz = student.createQuiz(course.getId(), "Observer", 6, Difficulty.MEDIUM,
                Arrays.asList(QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE, QuestionType.SHORT_ANSWER));
        Map<String, String> answers = new HashMap<>();
        int trueFalseCount = 0;
        int shortCount = 0;
        for (Question question : quiz.getQuestions()) {
            String answer = question.getCorrectAnswer();
            if (question instanceof MultipleChoiceQuestion) {
                answer = Integer.toString(((MultipleChoiceQuestion) question).getCorrectIndex() + 1);
            } else if (question.getType() == QuestionType.TRUE_FALSE) {
                if (trueFalseCount == 0) {
                    answer = "false";
                }
                trueFalseCount++;
            } else if (question.getType() == QuestionType.SHORT_ANSWER) {
                shortCount++;
                if (shortCount == 2) {
                    answer = "It only stores unrelated clients.";
                }
            }
            answers.put(question.getId(), answer);
        }
        llm.reply("{\"pointsEarned\":1,\"explanation\":\"The subject and subscribers are identified.\"}");
        llm.failNext();
        QuizAttempt started = student.startAttempt(course.getId(), quiz.getId());
        attempt = student.submitAttempt(course.getId(), started.getId(), answers);
        assertEquals(1, attempt.getPendingCount());
        assertEquals(5, student.getProgress(course.getId()).getGradedCount());
        assertEquals(Difficulty.HARD, student.suggestDifficulty(course.getId(), "Observer").getLevel());
    }

    private String cli(AgentFacade facade, String... arguments) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(0, new CLI(facade, new PrintStream(output), new PrintStream(errors)).runCommand(arguments));
        assertEquals("", errors.toString());
        return output.toString();
    }

    private void resolveAndPlan() {
        AgentFacade reviewer = new AgentFacade(config, llm);
        for (QuestionResult result : attempt.getResults()) {
            if (result.getStatus() == GradingStatus.PENDING) {
                reviewer.selfGrade(course.getId(), attempt.getId(), result.getQuestionId(), false);
            }
        }
        assertEquals(Difficulty.MEDIUM, reviewer.suggestDifficulty(course.getId(), "Observer").getLevel());
        llm.reply("Four of six graded Observer answers were fully correct.");
        ConceptStats topic = reviewer.getWeakTopics(course.getId()).getRankedTopics().get(0);
        assertEquals(6, topic.getGradedCount());
        assertEquals(4, topic.getCorrectCount());
        LocalDate today = LocalDate.now();
        JSONObject task = new JSONObject().put("date", today.toString()).put("type", "READ").put("conceptName", "Observer")
                .put("description", "Review the subject and subscriber responsibilities.").put("minutes", 30);
        llm.reply(new JSONObject().put("tasks", new JSONArray().put(task)).toString());
        plan = new AgentFacade(config, llm).createStudyPlan(course.getId(), today.plusDays(2), 1);
        String taskId = plan.getTasks().get(0).getId();
        cli(new AgentFacade(config, llm), "plan", "complete", plan.getId(), taskId, "--course", course.getId());
        new AgentFacade(config, llm).completeTask(course.getId(), plan.getId(), taskId);
    }

    private void prepareCompletedStudy() throws Exception {
        learnAndReview();
        takeMixedQuiz();
        resolveAndPlan();
    }

    private Path saved(String directory, String id) {
        return config.getDataDirectory().resolve(directory).resolve(id + ".json");
    }

    private void assertSavedStudy(AgentFacade reader) {
        Course restored = reader.getCourse(course.getId());
        assertEquals(sourceId, restored.getMaterial(material.getId()).getChunks().get(0).getId());
        assertEquals("A subject notifies its subscribers when its state changes.", restored.getMaterial(material.getId()).getSummary());
        assertEquals(sourceId, restored.getConcepts().get(0).getSourceChunkIds().get(0));
        assertEquals(1.0, restored.getFlashcard(card.getId()).getMastery(), 0.0001);
        QuizAttempt restoredAttempt = reader.getAttempt(course.getId(), attempt.getId());
        assertEquals(attempt.getAnswers(), restoredAttempt.getAnswers());
        assertEquals(attempt.getSubmittedAt(), restoredAttempt.getSubmittedAt());
        assertEquals(0, restoredAttempt.getPendingCount());
        assertTrue(reader.getStudyPlan(course.getId(), plan.getId()).getTasks().get(0).isDone());
        ProgressSummary progress = reader.getProgress(course.getId());
        assertEquals(1, progress.getReviewsDone());
        assertEquals(1, progress.getQuizzesTaken());
        assertEquals(6, progress.getGradedCount());
        assertEquals(4, progress.getCorrectCount());
        assertEquals(1, progress.getTasksDone());
    }

    @Test
    public void completeStudyAndDependentAgentPracticeSurviveRepeatedRestarts() throws Exception {
        prepareCompletedStudy();
        JSONObject ranking = new JSONObject().put("stepId", "rank").put("toolName", "weakTopics").put("arguments", new JSONObject());
        JSONObject practice = new JSONObject().put("stepId", "practice").put("toolName", "quiz").put("arguments",
                new JSONObject().put("topic", "$rank.topic").put("count", "1").put("types", "true-false"));
        JSONObject counts = new JSONObject().put("stepId", "counts").put("toolName", "progress").put("arguments", new JSONObject());
        llm.reply(new JSONObject().put("steps", new JSONArray().put(ranking).put(practice).put(counts)).toString());
        llm.reply("Observer is the graded topic available for more practice.");
        llm.reply(new JSONObject().put("questions", new JSONArray().put(
                draft("TRUE_FALSE", "Observers receive updates from a subject.", "true"))).toString());
        AgentResult result = new AgentFacade(config, llm).runAgent(course.getId(), null, "Find a graded topic, create one practice question and show progress.");
        assertEquals(ResultStatus.OK, result.getStatus());
        assertEquals("Observer", result.getResults().get("rank").getData().get("topic"));
        assertEquals("6", result.getResults().get("rank").getData().get("gradedCount"));
        String extraQuiz = result.getResults().get("practice").getData().get("quizId");
        assertFalse(quiz.getId().equals(extraQuiz));
        llm.reply(new JSONObject().put("steps", new JSONArray().put(counts)).toString());
        String followup = cli(new AgentFacade(config, llm), "agent", "Show the same saved study counts.",
                "--course", course.getId(), "--session", result.getSessionId(), "--json");
        assertEquals("OK", new JSONObject(followup).getString("status"));
        MockLLMClient unused = new MockLLMClient();
        AgentFacade reader = new AgentFacade(config, unused);
        assertSavedStudy(reader);
        assertEquals(2, reader.listQuizzes(course.getId()).size());
        assertEquals(1, reader.listAttempts(course.getId()).size());
        assertEquals(4, reader.getHistory(course.getId()).size());
        assertEquals(4, new MemoryRepository(config.getDataDirectory()).loadConversation(course.getId(), result.getSessionId()).size());
        assertEquals("Observer", reader.getQuiz(course.getId(), extraQuiz).getTopic());
        assertTrue(cli(reader, "progress", "--course", course.getId()).contains("graded answers: 6"));
        assertEquals(0, unused.getPrompts().size());
    }

    @Test
    public void rebuildingDerivedFilesRetainsTheCompletedWorkflowWithoutInventedHistory() throws Exception {
        prepareCompletedStudy();
        Path courseFile = saved("courses", course.getId());
        Path quizFile = saved("quizzes", quiz.getId());
        Path attemptFile = saved("attempts", attempt.getId());
        Path planFile = saved("plans", plan.getId());
        String[] before = {Files.readString(courseFile), Files.readString(quizFile), Files.readString(attemptFile), Files.readString(planFile)};
        Files.delete(config.getDataDirectory().resolve("progress.json"));
        Files.delete(config.getDataDirectory().resolve("history.json"));
        MockLLMClient unused = new MockLLMClient();
        AgentFacade restored = new AgentFacade(config, unused);
        assertSavedStudy(restored);
        List<StudyEvent> history = restored.getHistory(course.getId());
        assertEquals(3, history.size());
        assertTrue(history.toString().contains("Restored quiz attempt snapshot"));
        assertTrue(restored.getStorageWarnings().contains("earlier grading events are unavailable"));
        assertEquals(before[0], Files.readString(courseFile));
        assertEquals(before[1], Files.readString(quizFile));
        assertEquals(before[2], Files.readString(attemptFile));
        assertEquals(before[3], Files.readString(planFile));
        new AgentFacade(config, unused).completeTask(course.getId(), plan.getId(), plan.getTasks().get(0).getId());
        assertEquals(3, new AgentFacade(config, unused).getHistory(course.getId()).size());
        assertEquals(0, unused.getPrompts().size());
    }

    @Test
    public void failedLaterGenerationKeepsEveryEarlierStudyRecordAfterRestart() throws Exception {
        prepareCompletedStudy();
        Path[] sources = {saved("courses", course.getId()), saved("quizzes", quiz.getId()),
                saved("attempts", attempt.getId()), saved("plans", plan.getId()),
                config.getDataDirectory().resolve("progress.json"), config.getDataDirectory().resolve("history.json")};
        String[] before = new String[sources.length];
        for (int i = 0; i < sources.length; i++) {
            before[i] = Files.readString(sources[i]);
        }
        MockLLMClient failures = new MockLLMClient();
        for (int i = 0; i < 5; i++) {
            failures.failNext();
        }
        AgentFacade restarted = new AgentFacade(config, failures);
        try {
            restarted.summarize(course.getId(), material.getId(), "brief");
            fail("Failed generation must preserve the earlier summary.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Summary could not be generated"));
        }
        try {
            restarted.createQuiz(course.getId(), "Observer", 1, Difficulty.EASY, Arrays.asList(QuestionType.TRUE_FALSE));
            fail("Failed generation must not add an empty quiz.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No quiz was saved"));
        }
        assertEquals(5, failures.getPrompts().size());
        for (int i = 0; i < sources.length; i++) {
            assertEquals(before[i], Files.readString(sources[i]));
        }
        AgentFacade reader = new AgentFacade(config, new MockLLMClient());
        assertSavedStudy(reader);
        assertEquals(1, reader.listQuizzes(course.getId()).size());
        assertEquals(4, reader.getHistory(course.getId()).size());
    }
}
