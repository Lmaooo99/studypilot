package studypilot;

import java.util.Map;

public class AnswerTool implements Tool {
    private final CourseService courses;
    private final QuestionAnswerService answers;

    public AnswerTool(CourseService courses, QuestionAnswerService answers) {
        this.courses = courses;
        this.answers = answers;
    }

    public String getName() {
        return "answer";
    }

    public String getDescription() {
        return "Answer a course question using retrieved material and checked citation IDs.";
    }

    public String[] getRequiredArguments() {
        return new String[] {"question"};
    }

    public String[] getOptionalArguments() {
        return new String[0];
    }

    public ToolResult execute(Map<String, String> arguments) {
        Answer answer = answers.answer(courses.getCourse(arguments.get("courseId")), arguments.get("question"));
        if (!answer.hasValidCitation()) {
            return new ToolResult(ResultStatus.INSUFFICIENT_DATA, answer.toString(), answer.getText());
        }
        return new ToolResult(ResultStatus.OK, answer.toString(), "");
    }
}
