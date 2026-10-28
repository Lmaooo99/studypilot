package studypilot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StudyPlan {
    private final String planId;
    private final String courseId;
    private final LocalDate examDate;
    private final int hoursPerDay;
    private final List<StudyTask> tasks = new ArrayList<>();
    private final String notice;

    public StudyPlan(String courseId, LocalDate examDate, int hoursPerDay, List<StudyTask> tasks) {
        this(courseId, examDate, hoursPerDay, tasks, "");
    }

    public StudyPlan(String courseId, LocalDate examDate, int hoursPerDay, List<StudyTask> tasks, String notice) {
        this(UUID.randomUUID().toString(), courseId, examDate, hoursPerDay, tasks, notice);
    }

    public StudyPlan(String planId, String courseId, LocalDate examDate, int hoursPerDay,
                     List<StudyTask> tasks, String notice) {
        if (planId == null || planId.isBlank() || courseId == null || courseId.isBlank()
                || examDate == null || tasks == null || tasks.isEmpty() || notice == null) {
            throw new IllegalArgumentException("A study plan needs IDs, an exam date and at least one task.");
        }
        if (hoursPerDay < 1 || hoursPerDay > 24) {
            throw new IllegalArgumentException("Hours per day must be a whole number from 1 to 24.");
        }
        this.planId = planId;
        this.courseId = courseId;
        this.examDate = examDate;
        this.hoursPerDay = hoursPerDay;
        this.notice = notice;
        List<String> ids = new ArrayList<>();
        for (StudyTask task : tasks) {
            if (task == null || ids.contains(task.getId())) {
                throw new IllegalArgumentException("Study tasks must have distinct IDs.");
            }
            ids.add(task.getId());
            int position = 0;
            for (StudyTask current : this.tasks) {
                if (task.getDate().isBefore(current.getDate())) {
                    break;
                }
                position++;
            }
            this.tasks.add(position, task);
        }
    }

    public String getId() {
        return planId;
    }
    public String getCourseId() {
        return courseId;
    }
    public LocalDate getExamDate() {
        return examDate;
    }
    public int getHoursPerDay() {
        return hoursPerDay;
    }
    public String getNotice() {
        return notice;
    }

    public List<StudyTask> getTasks() {
        return new ArrayList<>(tasks);
    }

    public StudyTask getTask(String taskId) {
        for (StudyTask task : tasks) {
            if (task.getId().equals(taskId)) {
                return task;
            }
        }
        throw new IllegalArgumentException("Study task not found in this plan.");
    }

    public List<StudyTask> getTasksForDay(LocalDate day) {
        List<StudyTask> daily = new ArrayList<>();
        for (StudyTask task : tasks) {
            if (task.getDate().equals(day)) {
                daily.add(task);
            }
        }
        return daily;
    }

    public int getTotalMinutes(LocalDate day) {
        int total = 0;
        for (StudyTask task : getTasksForDay(day)) {
            if (task.getMinutes() > Integer.MAX_VALUE - total) {
                throw new IllegalArgumentException("The total task time is too large.");
            }
            total += task.getMinutes();
        }
        return total;
    }

    public String describe() {
        StringBuilder text = new StringBuilder("Plan ID: " + planId + "\nExam date: " + examDate
                + "\nDaily budget: " + hoursPerDay * 60 + " minutes\n");
        if (!notice.isBlank()) {
            text.append(notice + "\n");
        }
        for (StudyTask task : tasks) {
            text.append("\n" + task + "\nTask ID: " + task.getId() + "\n" + task.getDescription() + "\n");
        }
        return text.toString();
    }

    public String toString() {
        return "Exam " + examDate + " [" + planId.substring(0, 8) + "]";
    }
}
