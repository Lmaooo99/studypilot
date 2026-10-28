package studypilot;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;

public class StudyPlanGenerator {
    private final CourseService courseService;
    private final ProgressTracker tracker;
    private final LLMClient llmClient;
    private final PromptTemplates templates;
    private final ResponseParser parser;
    private final StudyPlanRepository repository;
    private final int minResults;

    public StudyPlanGenerator(CourseService courseService, ProgressTracker tracker, LLMClient llmClient,
                              PromptTemplates templates, ResponseParser parser,
                              StudyPlanRepository repository, int minResults) {
        this.courseService = courseService;
        this.tracker = tracker;
        this.llmClient = llmClient;
        this.templates = templates;
        this.parser = parser;
        this.repository = repository;
        this.minResults = minResults;
    }

    public StudyPlan generate(String courseId, LocalDate examDate, int hoursPerDay) {
        LocalDate today = LocalDate.now();
        checkInput(examDate, hoursPerDay, today);
        Course course = courseService.getCourse(courseId);
        if (course.getChunks().isEmpty()) {
            throw new IllegalArgumentException("Import course material before creating a study plan.");
        }
        List<ConceptStats> ranked = tracker.getRankedStats(courseId, minResults);
        String basePrompt = templates.studyPlanPrompt(context(course, ranked, today), examDate, hoursPerDay);
        String prompt = basePrompt;
        String problem = "No usable study tasks were returned.";
        boolean reducedScope = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            LLMResponse response = llmClient.generate(prompt);
            if (!response.isSuccess()) {
                problem = response.getErrorMessage();
                continue;
            }
            List<String> violations;
            try {
                List<StudyTask> tasks = parser.parseStudyTasks(response.getText());
                String notice = planNotice(ranked.isEmpty(), reducedScope);
                StudyPlan plan = new StudyPlan(courseId, examDate, hoursPerDay, tasks, notice);
                violations = validatePlan(plan, today);
                if (ranked.isEmpty() && (plan.getTasks().get(0).getType() != TaskType.DIAGNOSTIC_QUIZ
                        || !plan.getTasks().get(0).getDate().equals(today))) {
                    violations.add("Insufficient graded history: start with a DIAGNOSTIC_QUIZ today; do not claim a weakest topic.");
                }
                if (violations.isEmpty()) {
                    repository.savePlan(plan);
                    return plan;
                }
            } catch (JSONException | IllegalArgumentException | DateTimeException e) {
                violations = new ArrayList<>();
                violations.add("Return nonempty tasks with valid dates, types, topic names, descriptions and integer minutes.");
            }
            problem = violations.toString();
            if (problem.contains("Daily budget exceeded")) {
                reducedScope = true;
            }
            prompt = basePrompt + "\nFix these issues in the next proposal: " + problem;
        }
        throw new IllegalStateException("No valid study plan after 3 proposal attempts. " + problem);
    }

    private void checkInput(LocalDate examDate, int hoursPerDay, LocalDate today) {
        if (examDate == null || examDate.isBefore(today)) {
            throw new IllegalArgumentException("The exam date must be today or later.");
        }
        if (hoursPerDay < 1 || hoursPerDay > 24) {
            throw new IllegalArgumentException("Hours per day must be a whole number from 1 to 24.");
        }
    }

    private String planNotice(boolean diagnostic, boolean reducedScope) {
        String text = "";
        if (diagnostic) {
            text = "Insufficient graded history: start with the diagnostic quiz before identifying weak topics.\n";
        }
        if (reducedScope) {
            text += "An earlier proposal exceeded your daily budget. This plan fits the daily limit; review its scope before using it.";
        }
        return text;
    }

    private List<String> validatePlan(StudyPlan plan, LocalDate today) {
        List<String> problems = new ArrayList<>();
        for (StudyTask task : plan.getTasks()) {
            if (task.getDate().isBefore(today) || task.getDate().isAfter(plan.getExamDate())) {
                problems.add("Task dates must be between " + today + " and " + plan.getExamDate() + ".");
            }
            if (plan.getTotalMinutes(task.getDate()) > plan.getHoursPerDay() * 60) {
                problems.add("Daily budget exceeded on " + task.getDate() + ". Reduce task lengths or scope to fit the budget.");
            }
        }
        return problems;
    }

    private String context(Course course, List<ConceptStats> ranked, LocalDate today) {
        StringBuilder text = new StringBuilder("Today: " + today + "\nCourse: " + course.getName() + "\n");
        if (ranked.isEmpty()) {
            text.append("Insufficient graded history. Do not identify a weakest topic. Start with a DIAGNOSTIC_QUIZ today.\n");
        } else {
            text.append("Measured topics, lowest accuracy first; high accuracy does not imply weakness:\n");
            for (ConceptStats stats : ranked) {
                text.append(stats.getConceptName() + ": " + stats.getCorrectCount() + "/"
                        + stats.getGradedCount() + " correct\n");
            }
        }
        int count = 0;
        for (Chunk chunk : course.getChunks()) {
            if (count == 8) {
                break;
            }
            text.append("\n" + chunk.getLocation() + "\n" + chunk.getText() + "\n");
            count++;
        }
        return text.toString();
    }

    public StudyPlan getPlan(String courseId, String planId) {
        StudyPlan plan = repository.loadPlan(planId);
        if (!plan.getCourseId().equals(courseId)) {
            throw new IllegalArgumentException("Study plan not found in this course.");
        }
        return plan;
    }

    public List<StudyPlan> listPlans(String courseId) {
        return repository.listPlans(courseId);
    }

    public void markTaskDone(String courseId, String planId, String taskId) {
        StudyPlan plan = getPlan(courseId, planId);
        StudyTask task = plan.getTask(taskId);
        if (!task.isDone()) {
            task.markDone();
            repository.savePlan(plan);
        }
        // A repeated completion also repairs a failed progress or history save.
        tracker.recordTaskDone(courseId, task);
    }
}
