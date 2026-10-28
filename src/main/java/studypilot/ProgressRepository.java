package studypilot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.DateTimeException;
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
            item.put("attemptIds", new JSONArray(summary.getAttemptIds()));
            item.put("countedResultIds", new JSONArray(summary.getCountedResultIds()));
            item.put("completedTaskIds", new JSONArray(summary.getCompletedTaskIds()));
            JSONArray stats = new JSONArray();
            for (ConceptStats value : summary.getStats()) {
                JSONObject topic = new JSONObject();
                topic.put("conceptName", value.getConceptName());
                topic.put("gradedCount", value.getGradedCount());
                topic.put("correctCount", value.getCorrectCount());
                stats.put(topic);
            }
            item.put("stats", stats);
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
                List<ConceptStats> stats = new ArrayList<>();
                if (item.has("stats")) {
                    JSONArray savedStats = item.getJSONArray("stats");
                    for (int j = 0; j < savedStats.length(); j++) {
                        JSONObject topic = savedStats.getJSONObject(j);
                        stats.add(new ConceptStats(topic.getString("conceptName"),
                                topic.getInt("gradedCount"), topic.getInt("correctCount")));
                    }
                }
                ProgressSummary summary = new ProgressSummary(courseId, reviewsDone,
                        loadIds(item, "attemptIds"), loadIds(item, "countedResultIds"), stats,
                        loadIds(item, "completedTaskIds"));
                summaries.put(courseId, summary);
            }
        } catch (JSONException | IllegalStateException | IllegalArgumentException e) {
            recover(file, "progress");
            summaries.clear();
        }
        return summaries;
    }

    private List<String> loadIds(JSONObject data, String name) {
        List<String> ids = new ArrayList<>();
        // Older progress files do not contain every quiz or plan field.
        if (data.has(name)) {
            JSONArray values = data.getJSONArray(name);
            for (int i = 0; i < values.length(); i++) {
                ids.add(values.getString(i));
            }
        }
        return ids;
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
        } catch (JSONException | IllegalStateException | IllegalArgumentException | DateTimeException e) {
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
