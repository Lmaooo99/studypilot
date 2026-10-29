package studypilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuizTool implements Tool {
    private final CourseService courses;
    private final QuizService quizzes;

    public QuizTool(CourseService courses, QuizService quizzes) {
        this.courses = courses;
        this.quizzes = quizzes;
    }

    public String getName() {
        return "quiz";
    }

    public String getDescription() {
        return "Generate and save a quiz. count is 1-50 (default 6); difficulty defaults to the graded-history suggestion. "
                + "types is a CSV of multiple-choice,true-false,short-answer (default all three). count must be at least the number of types.";
    }

    public String[] getRequiredArguments() {
        return new String[] {"topic"};
    }

    public String[] getOptionalArguments() {
        return new String[] {"count", "difficulty", "types"};
    }

    public ToolResult execute(Map<String, String> arguments) {
        Course course = courses.getCourse(arguments.get("courseId"));
        if (course.getChunks().isEmpty()) {
            return new ToolResult(ResultStatus.INSUFFICIENT_DATA, "", "Please import course material before generating a quiz.");
        }
        String topic = arguments.get("topic");
        int count = Integer.parseInt(arguments.getOrDefault("count", "6"));
        Difficulty difficulty = quizzes.suggestDifficulty(course.getId(), topic).getLevel();
        if (arguments.containsKey("difficulty")) {
            difficulty = Difficulty.parse(arguments.get("difficulty"));
        }
        List<QuestionType> types = new ArrayList<>();
        for (String type : arguments.getOrDefault("types", "multiple-choice,true-false,short-answer").split(",", -1)) {
            types.add(QuestionType.parse(type));
        }
        Quiz quiz = quizzes.createQuiz(course, topic, count, difficulty, types);
        Map<String, String> data = new HashMap<>();
        data.put("quizId", quiz.getId());
        data.put("topic", topic);
        data.put("count", Integer.toString(quiz.getQuestions().size()));
        return new ToolResult(ResultStatus.OK, "Requested " + count + " questions; generated "
                + quiz.getQuestions().size() + ".\n" + quiz.describe(), "", data);
    }
}
