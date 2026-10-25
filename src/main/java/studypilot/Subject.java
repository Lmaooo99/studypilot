package studypilot;

import java.util.ArrayList;
import java.util.List;

public abstract class Subject {
    private final List<Observer> observers = new ArrayList<>();

    public void attach(Observer observer) {
        if (observer == null) {
            throw new IllegalArgumentException("An observer is required.");
        }
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void detach(Observer observer) {
        observers.remove(observer);
    }

    public void notifyObservers() {
        List<Observer> currentObservers = new ArrayList<>(observers);
        for (Observer observer : currentObservers) {
            observer.update(this);
        }
    }
}
