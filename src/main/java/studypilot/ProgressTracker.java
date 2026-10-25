package studypilot;

import java.util.Map;

public class ProgressTracker extends Subject {
    private final Map<String, ProgressSummary> summaries;
    private final ProgressRepository repository;
    private StudyEvent lastEvent;

    public ProgressTracker(ProgressRepository repository) {
        this.repository = repository;
        summaries = repository.loadSummaries();
    }

    public void recordReview(String courseId, Flashcard card, boolean remembered) {
        ProgressSummary summary = summaries.get(courseId);
        if (summary == null) {
            summary = new ProgressSummary(courseId, 0);
            summaries.put(courseId, summary);
        }
        summary.addReview();
        repository.saveSummaries(summaries);

        String description = "Reviewed flashcard " + card.getId();
        if (remembered) {
            description += ": remembered";
        } else {
            description += ": not yet";
        }
        lastEvent = new StudyEvent(courseId, card.getConceptName(), description);
        notifyObservers();
    }

    public ProgressSummary getSummary(String courseId) {
        ProgressSummary summary = summaries.get(courseId);
        if (summary == null) {
            return new ProgressSummary(courseId, 0);
        }
        return summary;
    }

    public StudyEvent getLastEvent() {
        return lastEvent;
    }
}
