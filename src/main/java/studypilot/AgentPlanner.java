package studypilot;

import java.time.LocalDate;
import java.util.List;
import org.json.JSONException;

public class AgentPlanner {
    private final LLMClient llmClient;
    private final ToolManager toolManager;
    private final PromptTemplates templates;
    private final ResponseParser parser;
    private final CourseService courses;

    public AgentPlanner(LLMClient llmClient, ToolManager toolManager, PromptTemplates templates,
                         ResponseParser parser, CourseService courses) {
        this.llmClient = llmClient;
        this.toolManager = toolManager;
        this.templates = templates;
        this.parser = parser;
        this.courses = courses;
    }

    public ExecutionPlan createPlan(String courseId, String request, List<Message> history) {
        return requestPlan(courseContext(courseId, request), history);
    }

    public ExecutionPlan replan(String courseId, String request, List<Message> history, String resultsSoFar) {
        String context = courseContext(courseId, request)
                + "\nREPLANNING DATA BEGIN\n" + resultsSoFar + "\nREPLANNING DATA END\n"
                + "Return a corrected plan for the remaining work. Successful steps are already complete. "
                + "Do not repeat them under new IDs or change their tool or arguments. "
                + "Reuse their IDs and data when needed. Failed steps may be corrected with valid arguments.";
        return requestPlan(context, history);
    }

    private String courseContext(String courseId, String request) {
        Course course = courses.getCourse(courseId);
        StringBuilder context = new StringBuilder("Today: " + LocalDate.now() + "\nCourse: " + course.getName() + "\nMaterials:\n");
        for (Material material : course.getMaterials()) {
            context.append(material.getId() + " - " + material.getFileName() + "\n");
        }
        context.append("Student request: " + request);
        return context.toString();
    }

    private ExecutionPlan requestPlan(String context, List<Message> history) {
        String prompt = templates.executionPlanPrompt(context, toolManager.getToolDescriptions(), history);
        String problem = "The reply was not a usable plan.";
        for (int attempt = 0; attempt < 2; attempt++) {
            LLMResponse response = llmClient.generate(prompt);
            if (response.isSuccess()) {
                try {
                    return parser.parseExecutionPlan(response.getText());
                } catch (JSONException | IllegalArgumentException e) {
                    problem = "Return the requested plan JSON with string arguments, or a clarification question.";
                }
            } else {
                problem = response.getErrorMessage();
            }
            prompt = templates.executionPlanPrompt(context, toolManager.getToolDescriptions(), history)
                    + "\nThe previous proposal was unusable. " + problem;
        }
        throw new IllegalStateException("The assistant could not create a plan after one retry. " + problem);
    }
}
