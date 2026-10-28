package studypilot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class StudyPlanRepository {
    private final Path directory;

    public StudyPlanRepository(Path dataDirectory) {
        directory = dataDirectory.resolve("plans");
    }

    public void savePlan(StudyPlan plan) {
        checkPlan(plan);
        JSONObject data = new JSONObject();
        data.put("planId", plan.getId());
        data.put("courseId", plan.getCourseId());
        data.put("examDate", plan.getExamDate().toString());
        data.put("hoursPerDay", plan.getHoursPerDay());
        data.put("notice", plan.getNotice());
        JSONArray tasks = new JSONArray();
        for (StudyTask task : plan.getTasks()) {
            JSONObject item = new JSONObject();
            item.put("taskId", task.getId());
            item.put("date", task.getDate().toString());
            item.put("type", task.getType().name());
            item.put("conceptName", task.getConceptName());
            item.put("description", task.getDescription());
            item.put("minutes", task.getMinutes());
            item.put("done", task.isDone());
            tasks.put(item);
        }
        data.put("tasks", tasks);
        JsonFiles.write(JsonFiles.namedFile(directory, plan.getId()), data);
    }

    public StudyPlan loadPlan(String planId) {
        Path file = JsonFiles.namedFile(directory, planId);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Study plan not found.");
        }
        JSONObject data = JsonFiles.read(file);
        try {
            if (!data.getString("planId").equals(planId) || !(data.get("hoursPerDay") instanceof Integer)) {
                throw new JSONException("Invalid plan ID or hours");
            }
            List<StudyTask> tasks = loadTasks(data.getJSONArray("tasks"));
            StudyPlan plan = new StudyPlan(planId, data.getString("courseId"),
                    LocalDate.parse(data.getString("examDate")), data.getInt("hoursPerDay"),
                    tasks, data.getString("notice"));
            checkPlan(plan);
            return plan;
        } catch (JSONException | IllegalArgumentException | DateTimeException e) {
            throw new IllegalStateException("Saved plan fields are invalid. The original file was not changed.");
        }
    }

    private List<StudyTask> loadTasks(JSONArray saved) {
        List<StudyTask> tasks = new ArrayList<>();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.getJSONObject(i);
            if (!(item.get("minutes") instanceof Integer) || !(item.get("done") instanceof Boolean)) {
                throw new JSONException("Invalid task minutes or completion flag");
            }
            tasks.add(new StudyTask(item.getString("taskId"), LocalDate.parse(item.getString("date")),
                    TaskType.valueOf(item.getString("type")), item.getString("conceptName"),
                    item.getString("description"), item.getInt("minutes"), item.getBoolean("done")));
        }
        return tasks;
    }

    private void checkPlan(StudyPlan plan) {
        for (StudyTask task : plan.getTasks()) {
            if (task.getDate().isAfter(plan.getExamDate())
                    || plan.getTotalMinutes(task.getDate()) > plan.getHoursPerDay() * 60) {
                throw new IllegalArgumentException("Plan tasks must fit the exam date and daily budget.");
            }
        }
    }

    public List<StudyPlan> listPlans(String courseId) {
        List<StudyPlan> plans = new ArrayList<>();
        if (!Files.exists(directory)) {
            return plans;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : entries) {
                files.add(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("The study plan directory could not be read.");
        }
        Collections.sort(files);
        for (Path file : files) {
            String name = file.getFileName().toString();
            StudyPlan plan = loadPlan(name.substring(0, name.length() - 5));
            if (plan.getCourseId().equals(courseId)) {
                plans.add(plan);
            }
        }
        return plans;
    }
}
