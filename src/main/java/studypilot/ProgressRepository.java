package studypilot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ProgressRepository {
    private final Path directory;
    private String recoveryWarning = "";
    public ProgressRepository(Path directory) {
        this.directory = directory;
    }

    public String getRecoveryWarning() {
        return recoveryWarning;
    }

    public void saveSummaries(Map<String, ProgressSummary> summaries) {
        JSONArray items = new JSONArray();
        for (ProgressSummary summary : summaries.values()) {
            JSONObject item = new JSONObject();
            item.put("courseId", summary.getCourseId());
            item.put("reviewsDone", summary.getReviewsDone());
            items.put(item);
        }
        JSONObject data = new JSONObject();
        data.put("summaries", items);
        JsonFiles.write(directory.resolve("progress.json"), data);
    }

    public Map<String, ProgressSummary> loadSummaries() {
        Map<String, ProgressSummary> summaries = new HashMap<>();
        Path file = directory.resolve("progress.json");
        if (!Files.exists(file)) {
            return summaries;
        }
        try {
            JSONArray items = JsonFiles.read(file).getJSONArray("summaries");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String courseId = item.getString("courseId");
                int reviewsDone = item.getInt("reviewsDone");
                ProgressSummary summary = new ProgressSummary(courseId, reviewsDone);
                summaries.put(courseId, summary);
            }
        } catch (JSONException | IllegalStateException | IllegalArgumentException e) {
            recover(file, "progress");
            summaries.clear();
        }
        return summaries;
    }

    public void saveHistory(List<StudyEvent> events) {
        JSONArray items = new JSONArray();
        for (StudyEvent event : events) {
            JSONObject item = new JSONObject();
            item.put("eventId", event.getId());
            item.put("time", event.getTime().toString());
            item.put("courseId", event.getCourseId());
            item.put("conceptName", event.getConceptName());
            item.put("description", event.getDescription());
            items.put(item);
        }
        JSONObject data = new JSONObject();
        data.put("events", items);
        JsonFiles.write(directory.resolve("history.json"), data);
    }

    public List<StudyEvent> loadHistory() {
        List<StudyEvent> events = new ArrayList<>();
        Path file = directory.resolve("history.json");
        if (!Files.exists(file)) {
            return events;
        }
        try {
            JSONArray items = JsonFiles.read(file).getJSONArray("events");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String id = item.getString("eventId");
                LocalDateTime time = LocalDateTime.parse(item.getString("time"));
                String courseId = item.getString("courseId");
                String conceptName = item.getString("conceptName");
                String description = item.getString("description");
                events.add(new StudyEvent(id, time, courseId, conceptName, description));
            }
        } catch (JSONException | IllegalStateException | IllegalArgumentException e) {
            recover(file, "history");
            events.clear();
        }
        return events;
    }

    private void recover(Path file, String kind) {
        Path backup = file.resolveSibling(file.getFileName() + ".bak-" + UUID.randomUUID());
        try {
            Files.move(file, backup);
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable data could not be backed up. The original file was not changed.");
        }
        recoveryWarning += "Unreadable " + kind + " was backed up; starting with empty " + kind + ".\n";
    }
}
