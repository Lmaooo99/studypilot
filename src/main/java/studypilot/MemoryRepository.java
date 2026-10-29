package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MemoryRepository {
    private final Path directory;

    public MemoryRepository(Path dataDirectory) {
        directory = dataDirectory.resolve("conversations");
    }

    public void saveConversation(String courseId, String sessionId, List<Message> messages) {
        Path file = JsonFiles.namedFile(directory, sessionId);
        if (Files.exists(file)) {
            loadConversation(courseId, sessionId);
        }
        JSONObject data = new JSONObject();
        data.put("sessionId", sessionId);
        data.put("courseId", courseId);
        JSONArray saved = new JSONArray();
        for (Message message : messages) {
            JSONObject item = new JSONObject();
            item.put("role", message.getRole());
            item.put("text", message.getText());
            saved.put(item);
        }
        data.put("messages", saved);
        JsonFiles.write(file, data);
    }

    public List<Message> loadConversation(String courseId, String sessionId) {
        Path file = JsonFiles.namedFile(directory, sessionId);
        List<Message> messages = new ArrayList<>();
        if (!Files.exists(file)) {
            return messages;
        }
        JSONObject data = JsonFiles.read(file);
        String savedCourse;
        try {
            if (!data.getString("sessionId").equals(sessionId)) {
                throw new JSONException("Session ID mismatch");
            }
            savedCourse = data.getString("courseId");
        } catch (JSONException e) {
            throw new IllegalStateException("Saved conversation fields are invalid. The original file was not changed.");
        }
        if (!savedCourse.equals(courseId)) {
            throw new IllegalArgumentException("This conversation belongs to another course.");
        }
        try {
            JSONArray saved = data.getJSONArray("messages");
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.getJSONObject(i);
                messages.add(new Message(item.getString("role"), item.getString("text")));
            }
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("Saved conversation fields are invalid. The original file was not changed.");
        }
        return messages;
    }
}
