package studypilot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuizAttempt {
    private final String attemptId;
    private final String quizId;
    private final String courseId;
    private final Map<String, String> answers;
    private final List<QuestionResult> results;
    private LocalDateTime submittedAt;

    public QuizAttempt(String quizId, String courseId) {
        this(UUID.randomUUID().toString(), quizId, courseId,
                new HashMap<String, String>(), new ArrayList<QuestionResult>(), null);
    }

    public QuizAttempt(String attemptId, String quizId, String courseId, Map<String, String> answers,
                       List<QuestionResult> results, LocalDateTime submittedAt) {
        if (!UUID.fromString(attemptId).toString().equals(attemptId)
                || !UUID.fromString(quizId).toString().equals(quizId)
                || !UUID.fromString(courseId).toString().equals(courseId)) {
            throw new IllegalArgumentException("Invalid quiz attempt IDs.");
        }
        if (submittedAt == null && (!answers.isEmpty() || !results.isEmpty())) {
            throw new IllegalArgumentException("An unsubmitted attempt cannot have saved answers or grades.");
        }
        this.attemptId = attemptId;
        this.quizId = quizId;
        this.courseId = courseId;
        this.answers = new HashMap<>(answers);
        this.results = new ArrayList<>(results);
        this.submittedAt = submittedAt;
    }

    public String getId() {
        return attemptId;
    }

    public String getQuizId() {
        return quizId;
    }

    public String getCourseId() {
        return courseId;
    }

    public Map<String, String> getAnswers() {
        return new HashMap<>(answers);
    }

    public List<QuestionResult> getResults() {
        return new ArrayList<>(results);
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public boolean isSubmitted() {
        return submittedAt != null;
    }

    public void submit(Map<String, String> answers) {
        if (isSubmitted()) {
            throw new IllegalStateException("This attempt has already been submitted.");
        }
        this.answers.putAll(answers);
        submittedAt = LocalDateTime.now();
    }

    public void addResult(QuestionResult result) {
        if (!isSubmitted()) {
            throw new IllegalStateException("Submit the answers before grading.");
        }
        for (QuestionResult previous : results) {
            if (previous.getQuestionId().equals(result.getQuestionId())) {
                throw new IllegalArgumentException("A question can have only one result in an attempt.");
            }
        }
        results.add(result);
    }

    public QuestionResult getResult(String questionId) {
        for (QuestionResult result : results) {
            if (result.getQuestionId().equals(questionId)) {
                return result;
            }
        }
        throw new IllegalArgumentException("Question result not found in this attempt.");
    }

    public void replacePendingResult(QuestionResult result) {
        for (int i = 0; i < results.size(); i++) {
            QuestionResult previous = results.get(i);
            if (previous.getQuestionId().equals(result.getQuestionId())) {
                if (previous.getStatus() != GradingStatus.PENDING) {
                    throw new IllegalArgumentException("Only a pending result can be replaced.");
                }
                results.set(i, result);
                return;
            }
        }
        throw new IllegalArgumentException("Question result not found in this attempt.");
    }

    public int getPendingCount() {
        int pending = 0;
        for (QuestionResult result : results) {
            if (result.getStatus() == GradingStatus.PENDING) {
                pending++;
            }
        }
        return pending;
    }

    public boolean isFullyGraded() {
        return isSubmitted() && !results.isEmpty() && getPendingCount() == 0;
    }

    public int getPointsEarned() {
        int points = 0;
        for (QuestionResult result : results) {
            if (result.getStatus() == GradingStatus.GRADED) {
                points += result.getPointsEarned();
            }
        }
        return points;
    }

    public int getGradedPoints() {
        int points = 0;
        for (QuestionResult result : results) {
            if (result.getStatus() == GradingStatus.GRADED) {
                points += result.getMaxPoints();
            }
        }
        return points;
    }

    public double getScore() {
        if (getGradedPoints() == 0) {
            return 0;
        }
        return 100.0 * getPointsEarned() / getGradedPoints();
    }

    public String describe(Quiz quiz) {
        StringBuilder text = new StringBuilder("Attempt ID: " + attemptId + "\nQuiz: " + quiz.getTopic());
        if (!isSubmitted()) {
            return text + "\nNot submitted. Multiple choice: use the displayed option number. True/false: use true or false.\n\n"
                    + quiz.describe();
        }
        text.append("\nSubmitted: " + submittedAt + "\n");
        if (getGradedPoints() == 0) {
            text.append("Score: not available (no graded answers).\n");
        } else {
            text.append("Score: " + getPointsEarned() + "/" + getGradedPoints() + " (" + Math.round(getScore()) + "%)\n");
        }
        text.append("Pending: " + getPendingCount() + "\n");
        for (QuestionResult result : results) {
            text.append("\n" + quiz.getQuestion(result.getQuestionId()).getText() + "\n");
            text.append(result.getStatus() + " - " + result.getExplanation() + "\n");
        }
        return text.toString();
    }

    public String toString() {
        String status = "not submitted";
        if (isSubmitted()) {
            status = "submitted, " + getPendingCount() + " pending";
        }
        return attemptId.substring(0, 8) + " (" + status + ")";
    }
}
