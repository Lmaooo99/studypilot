package studypilot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MemoryManager {
    private String sessionId;
    private String courseId;
    private final List<Message> messages = new ArrayList<>();
    private final MemoryRepository repository;

    public MemoryManager(MemoryRepository repository) {
        this.repository = repository;
    }

    public void loadSession(String courseId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        List<Message> saved = repository.loadConversation(courseId, sessionId);
        this.sessionId = sessionId;
        this.courseId = courseId;
        messages.clear();
        messages.addAll(saved);
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<Message> getHistory() {
        return new ArrayList<>(messages);
    }

    public void addMessage(Message message) {
        List<Message> updated = new ArrayList<>(messages);
        updated.add(message);
        repository.saveConversation(courseId, sessionId, updated);
        messages.add(message);
    }
}
