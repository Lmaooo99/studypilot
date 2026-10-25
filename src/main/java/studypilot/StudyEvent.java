package studypilot;

import java.time.LocalDateTime;
import java.util.UUID;

public class StudyEvent {
    private final String eventId;
    private final LocalDateTime time;
    private final String courseId;
    private final String conceptName;
    private final String description;

    public StudyEvent(String courseId, String conceptName, String description) {
        this(UUID.randomUUID().toString(), LocalDateTime.now(), courseId, conceptName, description);
    }

    public StudyEvent(String eventId, LocalDateTime time, String courseId, String conceptName, String description) {
        this.eventId = eventId;
        this.time = time;
        this.courseId = courseId;
        this.conceptName = conceptName;
        this.description = description;
    }

    public String getId() {
        return eventId;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getConceptName() {
        return conceptName;
    }

    public String getDescription() {
        return description;
    }

    public String toString() {
        String text = time + "  " + description;
        if (!conceptName.isEmpty()) {
            text += " [" + conceptName + "]";
        }
        return text;
    }
}
