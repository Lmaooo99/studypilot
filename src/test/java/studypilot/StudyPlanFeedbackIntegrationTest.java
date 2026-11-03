package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudyPlanFeedbackIntegrationTest {
    private Path workspace;
    private AppConfig config;
    private MockLLMClient llm;
    private AgentFacade facade;
    private Course course;
    private LocalDate today;
    private LocalDate exam;

    @Before
    public void setUp() throws Exception {
        workspace = Files.createTempDirectory("studypilot-plan-feedback-");
        config = new AppConfig(workspace);
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        course = facade.createCourse("Observer course");
        Path notes = workspace.resolve("observer.txt");
        Files.writeString(notes, "Observer lets a subject notify subscribers.");
        facade.importMaterial(course.getId(), notes.toString());
        today = LocalDate.now();
        exam = today.plusDays(2);
    }

    private QuizAttempt submitAnswers(Course selected, String topic, int correct, int wrong, int pending) {
        List<Question> questions = new ArrayList<>();
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < correct + wrong; i++) {
            Question question = new TrueFalseQuestion(topic + " statement " + i, topic, true);
            questions.add(question);
            answers.put(question.getId(), Boolean.toString(i < correct));
        }
        for (int i = 0; i < pending; i++) {
            Question question = new ShortAnswerQuestion("Explain " + topic + " part " + i, topic,
                    "The subject notifies subscribers.", "Mention notifications.");
            questions.add(question);
            answers.put(question.getId(), "The subject notifies subscribers.");
            llm.failNext();
        }
        Quiz quiz = new Quiz(selected.getId(), topic, Difficulty.MEDIUM, questions);
        new QuizRepository(config.getDataDirectory()).saveQuiz(quiz);
        QuizAttempt attempt = facade.startAttempt(selected.getId(), quiz.getId());
        return facade.submitAttempt(selected.getId(), attempt.getId(), answers);
    }

    private String proposal(LocalDate date, String type, int minutes) {
        JSONObject task = new JSONObject().put("date", date.toString()).put("type", type)
                .put("conceptName", "Observer").put("description", "Review subjects and subscribers.").put("minutes", minutes);
        return new JSONObject().put("tasks", new JSONArray().put(task)).toString();
    }

    @Test
    public void resolvingPendingDataChangesANewPlanButKeepsTheEarlierSchedule() throws Exception {
        QuizAttempt attempt = submitAnswers(course, "Observer", 4, 0, 1);
        llm.reply(proposal(today, "DIAGNOSTIC_QUIZ", 30));
        StudyPlan first = facade.createStudyPlan(course.getId(), exam, 1);
        Path firstFile = config.getDataDirectory().resolve("plans").resolve(first.getId() + ".json");
        String savedFirst = Files.readString(firstFile);
        assertTrue(first.getNotice().contains("Insufficient graded history"));
        facade.selfGrade(course.getId(), attempt.getId(), attempt.getResults().get(4).getQuestionId(), false);
        MockLLMClient planner = new MockLLMClient();
        planner.reply(proposal(today, "READ", 60));
        AgentFacade restarted = new AgentFacade(config, planner);
        StudyPlan second = restarted.createStudyPlan(course.getId(), exam, 1);
        assertEquals(TaskType.READ, second.getTasks().get(0).getType());
        assertTrue(planner.getPrompts().get(0).contains("Observer: 4/5 correct"));
        assertFalse(planner.getPrompts().get(0).contains("Insufficient graded history"));
        assertEquals(Difficulty.HARD, restarted.suggestDifficulty(course.getId(), "Observer").getLevel());
        assertFalse(first.getId().equals(second.getId()));
        assertEquals(savedFirst, Files.readString(firstFile));
        assertTrue(restarted.getStudyPlan(course.getId(), first.getId()).getNotice().contains("Insufficient graded history"));
        assertEquals(attempt.getAnswers(), restarted.getAttempt(course.getId(), attempt.getId()).getAnswers());
        assertEquals(attempt.getSubmittedAt(), restarted.getAttempt(course.getId(), attempt.getId()).getSubmittedAt());
    }

    @Test
    public void reviewTaskAndPendingCountsDoNotReplaceGradedEvidenceForPlanning() {
        submitAnswers(course, "Observer", 0, 0, 5);
        Course savedCourse = facade.getCourse(course.getId());
        Flashcard card = new Flashcard("Who receives updates?", "Subscribers.", "Observer");
        savedCourse.setFlashcards(Arrays.asList(card));
        new CourseRepository(config.getDataDirectory()).saveCourse(savedCourse);
        for (int i = 0; i < 5; i++) {
            facade.reviewCard(course.getId(), card.getId(), true);
        }
        StudyTask oldTask = new StudyTask(today, TaskType.READ, "Observer", "Read the notes.", 20);
        StudyPlan oldPlan = new StudyPlan(course.getId(), exam, 1, Arrays.asList(oldTask));
        new StudyPlanRepository(config.getDataDirectory()).savePlan(oldPlan);
        facade.completeTask(course.getId(), oldPlan.getId(), oldTask.getId());
        MockLLMClient planner = new MockLLMClient();
        planner.reply(proposal(today, "DIAGNOSTIC_QUIZ", 20));
        AgentFacade restarted = new AgentFacade(config, planner);
        StudyPlan diagnostic = restarted.createStudyPlan(course.getId(), exam, 1);
        assertEquals(5, restarted.getProgress(course.getId()).getReviewsDone());
        assertEquals(1, restarted.getProgress(course.getId()).getTasksDone());
        assertEquals(0, restarted.getProgress(course.getId()).getGradedCount());
        assertEquals(Difficulty.MEDIUM, restarted.suggestDifficulty(course.getId(), "Observer").getLevel());
        assertEquals(TaskType.DIAGNOSTIC_QUIZ, diagnostic.getTasks().get(0).getType());
        assertTrue(planner.getPrompts().get(0).contains("Insufficient graded history"));
        assertFalse(planner.getPrompts().get(0).contains("Measured topics"));
    }

    @Test
    public void correctedProposalsKeepMeasuredContextAndCompleteOneTaskAfterRestart() {
        submitAnswers(course, "Observer", 5, 0, 0);
        MockLLMClient planner = new MockLLMClient();
        planner.reply(proposal(today, "READ", 61));
        planner.reply(proposal(exam.plusDays(1), "READ", 60));
        planner.reply(proposal(exam, "READ", 60));
        AgentFacade restarted = new AgentFacade(config, planner);
        StudyPlan plan = restarted.createStudyPlan(course.getId(), exam, 1);
        assertEquals(3, planner.getPrompts().size());
        for (String prompt : planner.getPrompts()) {
            assertTrue(prompt.contains("Observer: 5/5 correct"));
        }
        assertTrue(planner.getPrompts().get(1).contains("Daily budget exceeded"));
        assertTrue(planner.getPrompts().get(2).contains("Task dates must be between"));
        assertTrue(plan.getNotice().contains("daily budget"));
        assertEquals(60, plan.getTotalMinutes(exam));
        String taskId = plan.getTasks().get(0).getId();
        AgentFacade completer = new AgentFacade(config, new MockLLMClient());
        completer.completeTask(course.getId(), plan.getId(), taskId);
        completer.completeTask(course.getId(), plan.getId(), taskId);
        AgentFacade reader = new AgentFacade(config, new MockLLMClient());
        assertTrue(reader.getStudyPlan(course.getId(), plan.getId()).getTask(taskId).isDone());
        assertEquals(1, reader.getProgress(course.getId()).getTasksDone());
        assertEquals(1, reader.getProgress(course.getId()).getQuizzesTaken());
        assertEquals(5, reader.getProgress(course.getId()).getGradedCount());
        assertEquals(2, reader.getHistory(course.getId()).size());
    }

    @Test
    public void exhaustedInvalidProposalsDoNotReplaceAnEarlierScheduleOrGrades() throws Exception {
        submitAnswers(course, "Observer", 2, 3, 0);
        llm.reply(proposal(today, "READ", 30));
        StudyPlan first = facade.createStudyPlan(course.getId(), exam, 1);
        Path planFile = config.getDataDirectory().resolve("plans").resolve(first.getId() + ".json");
        String savedPlan = Files.readString(planFile);
        String savedProgress = Files.readString(config.getDataDirectory().resolve("progress.json"));
        String savedHistory = Files.readString(config.getDataDirectory().resolve("history.json"));
        MockLLMClient invalid = new MockLLMClient();
        for (int i = 0; i < 3; i++) {
            invalid.reply(proposal(today, "READ", 61));
        }
        AgentFacade restarted = new AgentFacade(config, invalid);
        try {
            restarted.createStudyPlan(course.getId(), exam, 1);
            fail("Exhausted proposals must not replace the saved schedule.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("3 proposal attempts"));
        }
        assertEquals(3, invalid.getPrompts().size());
        assertEquals(1, restarted.listStudyPlans(course.getId()).size());
        assertEquals(savedPlan, Files.readString(planFile));
        assertEquals(savedProgress, Files.readString(config.getDataDirectory().resolve("progress.json")));
        assertEquals(savedHistory, Files.readString(config.getDataDirectory().resolve("history.json")));
    }

    @Test
    public void invalidInputAndOtherCourseGradesDoNotContaminatePlanContext() throws Exception {
        submitAnswers(course, "Observer", 0, 5, 0);
        Course other = facade.createCourse("Strategy course");
        Path notes = workspace.resolve("strategy.txt");
        Files.writeString(notes, "Strategy chooses an interchangeable algorithm.");
        facade.importMaterial(other.getId(), notes.toString());
        submitAnswers(other, "Strategy", 5, 0, 0);
        MockLLMClient planner = new MockLLMClient();
        AgentFacade restarted = new AgentFacade(config, planner);
        try {
            restarted.createStudyPlan(course.getId(), today.minusDays(1), 1);
            fail("Past exam dates must be rejected before generation.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("exam date"));
        }
        try {
            restarted.createStudyPlan(course.getId(), exam, 0);
            fail("An invalid daily budget must be rejected before generation.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Hours per day"));
        }
        assertEquals(0, planner.getPrompts().size());
        planner.reply(proposal(today, "READ", 30));
        StudyPlan plan = restarted.createStudyPlan(course.getId(), exam, 1);
        assertTrue(planner.getPrompts().get(0).contains("Observer: 0/5 correct"));
        assertFalse(planner.getPrompts().get(0).contains("Strategy: 5/5 correct"));
        assertFalse(planner.getPrompts().get(0).contains("interchangeable algorithm"));
        try {
            restarted.completeTask(other.getId(), plan.getId(), plan.getTasks().get(0).getId());
            fail("A task completion must retain its plan's course scope.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("this course"));
        }
        assertEquals(0, restarted.getProgress(other.getId()).getTasksDone());
        assertEquals(Difficulty.HARD, restarted.suggestDifficulty(other.getId(), "Strategy").getLevel());
        assertEquals(Difficulty.EASY, restarted.suggestDifficulty(course.getId(), "Observer").getLevel());
    }
}
