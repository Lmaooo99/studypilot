package studypilot;

import java.util.HashMap;
import java.util.Map;

public class WeakTopicTool implements Tool {
    private final CourseService courses;
    private final WeakTopicAnalyzer analyzer;

    public WeakTopicTool(CourseService courses, WeakTopicAnalyzer analyzer) {
        this.courses = courses;
        this.analyzer = analyzer;
    }

    public String getName() {
        return "weakTopics";
    }

    public String getDescription() {
        return "Read the graded-topic ranking. Insufficient graded history requires clarification, not a weakest-topic claim.";
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
        WeakTopicReport report = analyzer.analyze(courseId);
        if (report.getRankedTopics().isEmpty()) {
            return new ToolResult(ResultStatus.INSUFFICIENT_DATA, report.toString(),
                    "There is not enough graded data to identify a weak topic. Which topic would you like to study, or would you prefer a diagnostic quiz?");
        }
        ConceptStats topic = report.getRankedTopics().get(0);
        Map<String, String> data = new HashMap<>();
        data.put("topic", topic.getConceptName());
        data.put("accuracy", Double.toString(topic.getAccuracy()));
        data.put("gradedCount", Integer.toString(topic.getGradedCount()));
        return new ToolResult(ResultStatus.OK, report.toString(), "", data);
    }
}
