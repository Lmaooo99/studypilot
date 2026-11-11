package studypilot;

import java.util.ArrayList;
import java.util.List;

public class HistoryRecorder implements Observer {
    private final ProgressTracker tracker;
    private final List<StudyEvent> history;
    private final ProgressRepository repository;
    private boolean savePending;

    public HistoryRecorder(ProgressTracker tracker, ProgressRepository repository) {
        this.tracker = tracker;
        this.repository = repository;
        List<StudyEvent> saved;
        try {
            saved = repository.loadHistory();
        } catch (IllegalStateException e) {
            saved = new ArrayList<>();
            repository.addWarning("history.json could not be read. Saved study records will be used; the blocked path was kept.");
        }
        history = saved;
    }

    public void update(Subject subject) {
        StudyEvent event = tracker.getLastEvent();
        if (event == null) {
            return;
        }
        record(event);
    }

    private synchronized void record(StudyEvent event) {
        if (contains(event.getId())) {
            saveIfMissing();
            return;
        }
        history.add(event);
        savePending = true;
        saveIfMissing();
    }

    public synchronized boolean contains(String eventId) {
        for (StudyEvent event : history) {
            if (event.getId().equals(eventId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void saveIfMissing() {
        if (!history.isEmpty() && (savePending || !repository.hasHistoryFile())) {
            try {
                repository.saveHistory(history);
                savePending = false;
            } catch (IllegalStateException e) {
                savePending = true;
                repository.addWarning("Study records are saved, but history.json could not be updated. "
                        + "Events are kept in memory; a later course read will retry the save.");
            }
        }
    }

    public synchronized List<StudyEvent> getHistory(String courseId) {
        List<StudyEvent> events = new ArrayList<>();
        for (StudyEvent event : history) {
            if (event.getCourseId().equals(courseId)) {
                int position = 0;
                while (position < events.size() && !event.getTime().isBefore(events.get(position).getTime())) {
                    position++;
                }
                events.add(position, event);
            }
        }
        return events;
    }
}
