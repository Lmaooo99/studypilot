package studypilot;

import java.util.List;

public class AgentFacade {
    private final CourseService courseService;
    private final SummaryService summaryService;
    private final ConceptService conceptService;
    private final QuestionAnswerService questionAnswerService;

    public AgentFacade(AppConfig config) {
        this(config, new OpenAIAdapter(config.getModel(), new OpenAIApiClient(config), config.getReasoningEffort()));
    }

    public AgentFacade(AppConfig config, LLMClient llmClient) {
        CourseRepository repository = new CourseRepository(config.getDataDirectory());
        PromptTemplates templates = new PromptTemplates();
        ResponseParser parser = new ResponseParser();
        courseService = new CourseService(repository);
        summaryService = new SummaryService(llmClient, templates, repository);
        conceptService = new ConceptService(llmClient, templates, parser, repository);
        RetrievalStrategy retrieval;
        if (config.getRetrievalMode().equals("keyword")) {
            retrieval = new KeywordRetrieval();
        } else {
            retrieval = new TfIdfRetrieval();
        }
        questionAnswerService = new QuestionAnswerService(retrieval, llmClient, templates, parser, new CitationValidator());
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

    public List<Concept> extractConcepts(String courseId, String materialId) {
        Course course = getCourse(courseId);
        return conceptService.extractConcepts(course, course.getMaterial(materialId));
    }

    public Answer askQuestion(String courseId, String question) {
        return questionAnswerService.answer(getCourse(courseId), question);
    }

    public String getImplementationStatus() {
        return "Available: courses, TXT/Markdown/PDF import, AI summaries, concepts and cited questions."
                + "\nFlashcards, quizzes, study plans and the agent are not available yet.";
    }
}
