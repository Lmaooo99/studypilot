package studypilot;

import java.util.HashMap;
import java.util.Map;

public class SummarizeTool implements Tool {
    private final CourseService courses;
    private final SummaryService summaries;

    public SummarizeTool(CourseService courses, SummaryService summaries) {
        this.courses = courses;
        this.summaries = summaries;
    }

    public String getName() {
        return "summarize";
    }

    public String getDescription() {
        return "Summarize one imported material. style is brief (default), detailed or bullet.";
    }

    public String[] getRequiredArguments() {
        return new String[] {"materialId"};
    }

    public String[] getOptionalArguments() {
        return new String[] {"style"};
    }

    public ToolResult execute(Map<String, String> arguments) {
        Course course = courses.getCourse(arguments.get("courseId"));
        if (course.getMaterials().isEmpty()) {
            return new ToolResult(ResultStatus.INSUFFICIENT_DATA, "", "Please import course material before requesting a summary.");
        }
        Material material = course.getMaterial(arguments.get("materialId"));
        String text = summaries.summarize(course, material, arguments.getOrDefault("style", "brief"));
        Map<String, String> data = new HashMap<>();
        data.put("materialId", material.getId());
        return new ToolResult(ResultStatus.OK, text, "", data);
    }
}
