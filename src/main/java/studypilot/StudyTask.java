package studypilot;

import java.time.LocalDate;
import java.util.UUID;

public class StudyTask {
    private final String taskId;
    private final LocalDate date;
    private final TaskType type;
    private final String conceptName;
    private final String description;
    private final int minutes;
    private boolean done;

    public StudyTask(LocalDate date, TaskType type, String conceptName, String description, int minutes) {
        this(UUID.randomUUID().toString(), date, type, conceptName, description, minutes, false);
    }

    public StudyTask(String taskId, LocalDate date, TaskType type, String conceptName,
                     String description, int minutes, boolean done) {
        if (taskId == null || taskId.isBlank() || date == null || type == null
                || conceptName == null || conceptName.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("A study task needs an ID, date, type, topic and description.");
        }
        if (minutes < 1 || minutes > 1440) {
            throw new IllegalArgumentException("Task minutes must be between 1 and 1440.");
        }
        this.taskId = taskId;
        this.date = date;
        this.type = type;
        this.conceptName = conceptName.trim();
        this.description = description.trim();
        this.minutes = minutes;
        this.done = done;
    }

    public String getId() {
        return taskId;
    }
    public LocalDate getDate() {
        return date;
    }
    public TaskType getType() {
        return type;
    }
    public String getConceptName() {
        return conceptName;
    }
    public String getDescription() {
        return description;
    }
    public int getMinutes() {
        return minutes;
    }
    public boolean isDone() {
        return done;
    }

    public void markDone() {
        done = true;
    }

    public String toString() {
        String state = "To do";
        if (done) {
            state = "Done";
        }
        return date + " - " + type + " - " + conceptName + " (" + minutes + " min, " + state + ")";
    }
}
