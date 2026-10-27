package studypilot;

import java.util.HashMap;
import java.util.Map;

public class QuizGrader {
    private final Map<QuestionType, GradingStrategy> strategies = new HashMap<>();

    public QuizGrader() {
        GradingStrategy exactMatch = new ExactMatchGrading();
        strategies.put(QuestionType.MULTIPLE_CHOICE, exactMatch);
        strategies.put(QuestionType.TRUE_FALSE, exactMatch);
    }

    public void grade(Quiz quiz, QuizAttempt attempt) {
        if (!quiz.getId().equals(attempt.getQuizId()) || !quiz.getCourseId().equals(attempt.getCourseId())) {
            throw new IllegalArgumentException("The attempt does not belong to this quiz.");
        }
        Map<String, String> answers = attempt.getAnswers();
        for (Question question : quiz.getQuestions()) {
            String answer = answers.getOrDefault(question.getId(), "");
            GradingStrategy strategy = strategies.get(question.getType());
            QuestionResult result;
            if (strategy != null) {
                result = strategy.grade(question, answer);
            } else if (answer.isBlank()) {
                result = new QuestionResult(question, GradingStatus.GRADED, 0, "Unanswered.");
            } else {
                result = new QuestionResult(question, GradingStatus.PENDING, 0, "Short-answer grading is pending.");
            }
            attempt.addResult(result);
        }
    }
}
