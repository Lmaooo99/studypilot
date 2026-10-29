package studypilot;

import java.util.HashMap;
import java.util.Map;

public class ProgressTool implements Tool {
    private final CourseService courses;
    private final ProgressTracker tracker;

    public ProgressTool(CourseService courses, ProgressTracker tracker) {
        this.courses = courses;
        this.tracker = tracker;
    }

    public String getName() {
        return "progress";
    }

    public String getDescription() {
        return "Read this course's actual review, quiz, graded-answer and completed-task counts.";
    }

    public String[] getRequiredArguments() {
        return new String[0];
    }

    public String[] getOptionalArguments() {
        return new String[0];
    }

    public ToolResult execute(Map<String, String> arguments) {
        String courseId = arguments.get("courseId");
        courses.getCourse(courseId);
        ProgressSummary summary = tracker.getSummary(courseId);
        Map<String, String> data = new HashMap<>();
        data.put("reviewsDone", Integer.toString(summary.getReviewsDone()));
        data.put("quizzesTaken", Integer.toString(summary.getQuizzesTaken()));
        data.put("gradedCount", Integer.toString(summary.getGradedCount()));
        data.put("correctCount", Integer.toString(summary.getCorrectCount()));
        data.put("tasksDone", Integer.toString(summary.getTasksDone()));
        return new ToolResult(ResultStatus.OK, summary.toString(), "", data);
    }
}
