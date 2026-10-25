package studypilot;

import java.util.List;

public class AgentFacade {
    private final CourseService courseService;
    private final SummaryService summaryService;

    public AgentFacade(AppConfig config) {
        this(config, new OpenAIAdapter(config.getModel(), new OpenAIApiClient(config), config.getReasoningEffort()));
    }

    public AgentFacade(AppConfig config, LLMClient llmClient) {
        CourseRepository repository = new CourseRepository(config.getDataDirectory());
        courseService = new CourseService(repository);
        summaryService = new SummaryService(llmClient, new PromptTemplates(), repository);
    }

    public Course createCourse(String name) {
        return courseService.createCourse(name);
    }

    public List<Course> listCourses() {
        return courseService.listCourses();
    }

    public Course getCourse(String courseId) {
        return courseService.getCourse(courseId);
    }

    public Material importMaterial(String courseId, String filePath) {
        return courseService.importMaterial(courseId, filePath);
    }

    public String summarize(String courseId, String materialId, String style) {
        Course course = getCourse(courseId);
        return summaryService.summarize(course, course.getMaterial(materialId), style);
    }

    public String getImplementationStatus() {
        return "Available: courses, TXT/Markdown/PDF import and AI material summaries."
                + "\nConcepts, questions, flashcards, quizzes, study plans and the agent are not available yet.";
    }
}
