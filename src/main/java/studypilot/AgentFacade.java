package studypilot;

public class AgentFacade {
    private final AppConfig config;

    public AgentFacade(AppConfig config) {
        this.config = config;
    }

    public String getImplementationStatus() {
        return "StudyPilot Stage 2: application startup and local configuration are available."
                + "\nConfigured model: " + config.getModel()
                + "\nFeature implementation is in progress.";
    }
}
