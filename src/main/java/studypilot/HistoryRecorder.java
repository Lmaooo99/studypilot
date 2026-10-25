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
        for (StudyEvent previous : history) {
            if (previous.getId().equals(event.getId())) {
                return;
            }
        }
        history.add(event);
        repository.saveHistory(history);
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
