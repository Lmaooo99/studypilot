package studypilot;

import java.util.List;

public class AgentFacade {
    private final CourseService courseService;
    private final SummaryService summaryService;
    private final ConceptService conceptService;
    private final QuestionAnswerService questionAnswerService;
    private final FlashcardService flashcardService;
    private final ProgressTracker progressTracker;
    private final ProgressRepository progressRepository;
    private final HistoryRecorder historyRecorder;

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
        progressRepository = new ProgressRepository(config.getDataDirectory());
        progressTracker = new ProgressTracker(progressRepository);
        historyRecorder = new HistoryRecorder(progressTracker, progressRepository);
        progressTracker.attach(historyRecorder);
        flashcardService = new FlashcardService(llmClient, templates, parser, repository, progressTracker);
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

    public List<Flashcard> generateFlashcards(String courseId, String materialId, int count) {
        Course course = getCourse(courseId);
        return flashcardService.generateCards(course, course.getMaterial(materialId), count);
    }

    public List<Flashcard> getCardsToReview(String courseId) {
        return flashcardService.getCardsToReview(getCourse(courseId));
    }

    public void reviewCard(String courseId, String cardId, boolean remembered) {
        Course course = getCourse(courseId);
        flashcardService.review(course, course.getFlashcard(cardId), remembered);
    }

    public ProgressSummary getProgress(String courseId) {
        getCourse(courseId);
        return progressTracker.getSummary(courseId);
    }

    public List<StudyEvent> getHistory(String courseId) {
        getCourse(courseId);
        return historyRecorder.getHistory(courseId);
    }

    public String getStorageWarnings() {
        return progressRepository.getRecoveryWarning();
    }

    public void observeProgress(Observer observer) {
        progressTracker.attach(observer);
    }

    public String getImplementationStatus() {
        return "Available: F01-F05 (courses, materials, summaries, concepts, cited questions, flashcards and review)."
                + "\nF11 currently records reviews and their history. Quizzes, study plans and the agent are planned for later periods.";
    }
}
