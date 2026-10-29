package studypilot;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class StudyPlanTool implements Tool {
    private final CourseService courses;
    private final StudyPlanGenerator generator;

    public StudyPlanTool(CourseService courses, StudyPlanGenerator generator) {
        this.courses = courses;
        this.generator = generator;
    }

    public String getName() {
        return "studyPlan";
    }

    public String getDescription() {
        return "Generate and save a revision schedule. examDate is YYYY-MM-DD, today or later; hoursPerDay is a whole number 1-24.";
    }

    public String[] getRequiredArguments() {
        return new String[] {"examDate", "hoursPerDay"};
    }

    public String[] getOptionalArguments() {
        return new String[0];
    }

    public ToolResult execute(Map<String, String> arguments) {
        if (courses.getCourse(arguments.get("courseId")).getChunks().isEmpty()) {
            return new ToolResult(ResultStatus.INSUFFICIENT_DATA, "", "Please import course material before creating a study plan.");
        }
        StudyPlan plan = generator.generate(arguments.get("courseId"), LocalDate.parse(arguments.get("examDate")),
                Integer.parseInt(arguments.get("hoursPerDay")));
        Map<String, String> data = new HashMap<>();
        data.put("planId", plan.getId());
        data.put("examDate", plan.getExamDate().toString());
        return new ToolResult(ResultStatus.OK, plan.describe(), "", data);
    }
}
