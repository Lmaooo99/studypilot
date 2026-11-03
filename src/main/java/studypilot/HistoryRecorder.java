package studypilot;

import java.util.ArrayList;
import java.util.List;

public class HistoryRecorder implements Observer {
    private final ProgressTracker tracker;
    private final List<StudyEvent> history;
    private final ProgressRepository repository;

    public HistoryRecorder(ProgressTracker tracker, ProgressRepository repository) {
        this.tracker = tracker;
        this.repository = repository;
        history = repository.loadHistory();
    }

    public void update(Subject subject) {
        StudyEvent event = tracker.getLastEvent();
        if (event == null) {
            return;
        }
        if (contains(event.getId())) {
            saveIfMissing();
            return;
        }
        List<StudyEvent> changed = new ArrayList<>(history);
        changed.add(event);
        repository.saveHistory(changed);
        history.add(event);
    }

    public boolean contains(String eventId) {
        for (StudyEvent event : history) {
            if (event.getId().equals(eventId)) {
                return true;
            }
        }
        return false;
    }

    public void saveIfMissing() {
        if (!history.isEmpty() && !repository.hasHistoryFile()) {
            repository.saveHistory(history);
        }
    }

    public List<StudyEvent> getHistory(String courseId) {
        List<StudyEvent> events = new ArrayList<>();
        for (StudyEvent event : history) {
            if (event.getCourseId().equals(courseId)) {
                events.add(event);
            }
        }
        return events;
    }
}
