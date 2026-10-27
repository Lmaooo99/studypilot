package studypilot;

public interface GradingStrategy {
    QuestionResult grade(Question question, String answer);
}
