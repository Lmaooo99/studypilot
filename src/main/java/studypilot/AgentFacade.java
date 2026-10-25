package studypilot;

import java.util.List;

public class AgentFacade {
    private final CourseService courseService;

    public AgentFacade(AppConfig config) {
        CourseRepository repository = new CourseRepository(config.getDataDirectory());
        courseService = new CourseService(repository);
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

    public String getImplementationStatus() {
        return "Available: courses and TXT/Markdown/PDF import with source locations."
                + "\nThe AI study features are not available yet.";
    }
}
