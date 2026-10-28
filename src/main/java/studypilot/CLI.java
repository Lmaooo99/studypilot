package studypilot;

import java.io.PrintStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.json.JSONException;

public class CLI {
    private final AgentFacade facade;
    private final PrintStream output;
    private final PrintStream errors;

    public CLI(AgentFacade facade, PrintStream output, PrintStream errors) {
        this.facade = facade;
        this.output = output;
        this.errors = errors;
    }

    public static void main(String[] args) {
        try {
            AppConfig config = new AppConfig(Path.of("."));
            AgentFacade facade = new AgentFacade(config);
            if (args.length > 0 && args[0].equals("gui")) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        new SwingGUI(facade).showMainWindow();
                    }
                });
            } else {
                CLI cli = new CLI(facade, System.out, System.err);
                int code = cli.runCommand(args);
                if (code != 0) {
                    System.exit(code);
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public int runCommand(String[] args) {
        String warnings = facade.getStorageWarnings();
        if (!warnings.isBlank()) {
            errors.print(warnings);
        }
        try {
            return execute(args);
        } catch (IllegalArgumentException e) {
            errors.println(e.getMessage());
            return 2;
        } catch (IllegalStateException e) {
            errors.println(e.getMessage());
            return 1;
        }
    }

    private int execute(String[] args) {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            showHelp();
            return 0;
        }
        String command = args[0];
        if (command.equals("status")) {
            output.println(facade.getImplementationStatus());
            return 0;
        } else if (command.equals("course")) {
            return courseCommand(args);
        } else if (command.equals("material")) {
            return materialCommand(args);
        } else if (command.equals("summarize")) {
            return summarizeCommand(args);
        } else if (command.equals("concepts")) {
            return conceptsCommand(args);
        } else if (command.equals("ask")) {
            return askCommand(args);
        } else if (command.equals("flashcards")) {
            return flashcardsCommand(args);
        } else if (command.equals("review")) {
            return reviewCommand(args);
        } else if (command.equals("quiz")) {
            return quizCommand(args);
        } else if (command.equals("progress") || command.equals("history")) {
            return progressCommand(args);
        }
        return unknownCommand();
    }

    private void showHelp() {
        output.println("StudyPilot");
        output.println("  gui     Open the graphical interface");
        output.println("  status  Show implementation status");
        output.println("  help    Show this help");
        output.println("  course add <name>");
        output.println("  course list");
        output.println("  material import <file> --course <id>");
        output.println("  material list --course <id>");
        output.println("  summarize <materialId> --course <id> [--style brief|detailed|bullet]");
        output.println("  concepts extract <materialId> --course <id>");
        output.println("  concepts list --course <id>");
        output.println("  ask <question> --course <id>");
        output.println("  flashcards generate <materialId> --course <id> [--count <number>]");
        output.println("  flashcards show <cardId> --course <id>");
        output.println("  review <courseId> [--card <cardId> --remembered true|false]");
        output.println("  quiz create --course <id> --topic <topic> [--count <number>] [--difficulty easy|medium|hard]");
        output.println("      [--types multiple-choice,true-false,short-answer]");
        output.println("  quiz list --course <id>");
        output.println("  quiz show <quizId> --course <id>");
        output.println("  quiz take <quizId> --course <id>");
        output.println("  quiz submit <attemptId> --course <id> --answers <jsonFile>");
        output.println("  quiz attempt <attemptId> --course <id>");
        output.println("  quiz attempts --course <id>");
        output.println("  quiz regrade <attemptId> --course <id>");
        output.println("  quiz model-answer <attemptId> <questionId> --course <id>");
        output.println("  quiz selfgrade <attemptId> <questionId> <right|wrong> --course <id>");
        output.println("  progress --course <id>");
        output.println("  history --course <id>");
    }

    private int courseCommand(String[] args) {
        if (args.length == 3 && args[1].equals("add")) {
            Course course = facade.createCourse(args[2]);
            output.println("Course ID: " + course.getId() + "\nName: " + course.getName());
            return 0;
        }
        if (args.length == 2 && args[1].equals("list")) {
            for (Course course : facade.listCourses()) {
                output.println(course.getId() + "  " + course.getName());
            }
            return 0;
        }
        return unknownCommand();
    }

    private int materialCommand(String[] args) {
        if (args.length >= 3 && args[1].equals("import")) {
            Map<String, String> flags = options(args, 3, "--course");
            String courseId = required(flags, "--course");
            Material material = facade.importMaterial(courseId, args[2]);
            output.println("Material ID: " + material.getId());
            output.println("File: " + material.getFileName());
            output.println("Chunks: " + material.getChunks().size());
            return 0;
        }
        if (args.length >= 2 && args[1].equals("list")) {
            Map<String, String> flags = options(args, 2, "--course");
            String courseId = required(flags, "--course");
            for (Material material : facade.getCourse(courseId).getMaterials()) {
                output.println(material.getId() + "  " + material.getFileName());
            }
            return 0;
        }
        return unknownCommand();
    }

    private int summarizeCommand(String[] args) {
        if (args.length < 2) {
            return unknownCommand();
        }
        Map<String, String> flags = options(args, 2, "--course", "--style");
        String courseId = required(flags, "--course");
        String style = flags.getOrDefault("--style", "brief");
        output.println(facade.summarize(courseId, args[1], style));
        return 0;
    }

    private int conceptsCommand(String[] args) {
        if (args.length >= 3 && args[1].equals("extract")) {
            Map<String, String> flags = options(args, 3, "--course");
            String courseId = required(flags, "--course");
            List<Concept> concepts = facade.extractConcepts(courseId, args[2]);
            if (concepts.isEmpty()) {
                output.println("No concepts found. Existing concepts were kept.");
            }
            for (Concept concept : concepts) {
                output.println(concept);
            }
            return 0;
        }
        if (args.length >= 2 && args[1].equals("list")) {
            Map<String, String> flags = options(args, 2, "--course");
            String courseId = required(flags, "--course");
            for (Concept concept : facade.getCourse(courseId).getConcepts()) {
                output.println(concept);
            }
            return 0;
        }
        return unknownCommand();
    }

    private int askCommand(String[] args) {
        if (args.length < 2) {
            return unknownCommand();
        }
        Map<String, String> flags = options(args, 2, "--course");
        String courseId = required(flags, "--course");
        output.println(facade.askQuestion(courseId, args[1]));
        return 0;
    }

    private int flashcardsCommand(String[] args) {
        if (args.length < 3) {
            return unknownCommand();
        }
        if (args[1].equals("generate")) {
            Map<String, String> flags = options(args, 3, "--course", "--count");
            String courseId = required(flags, "--course");
            int count = Integer.parseInt(flags.getOrDefault("--count", "5"));
            List<Flashcard> cards = facade.generateFlashcards(courseId, args[2], count);
            output.println("Generated " + cards.size() + " new cards; requested " + count + ".");
            for (Flashcard card : cards) {
                output.println(card.getId() + "  " + card.getFront());
            }
            return 0;
        }
        if (args[1].equals("show")) {
            Map<String, String> flags = options(args, 3, "--course");
            String courseId = required(flags, "--course");
            Flashcard card = facade.getCourse(courseId).getFlashcard(args[2]);
            output.println("Front: " + card.getFront() + "\nBack: " + card.getBack());
            return 0;
        }
        return unknownCommand();
    }

    private int reviewCommand(String[] args) {
        if (args.length < 2) {
            return unknownCommand();
        }
        Map<String, String> flags = options(args, 2, "--card", "--remembered");
        if (flags.isEmpty()) {
            List<Flashcard> cards = facade.getCardsToReview(args[1]);
            if (cards.isEmpty()) {
                output.println("No flashcards to review.");
            }
            for (Flashcard card : cards) {
                output.println(card.getId() + "  " + card.getFront() + "  mastery=" + card.getMastery());
            }
        } else {
            String remembered = required(flags, "--remembered");
            if (!remembered.equals("true") && !remembered.equals("false")) {
                throw new IllegalArgumentException("--remembered must be true or false.");
            }
            String cardId = required(flags, "--card");
            facade.reviewCard(args[1], cardId, Boolean.parseBoolean(remembered));
            output.println("Review recorded.");
        }
        return 0;
    }

    private int progressCommand(String[] args) {
        Map<String, String> flags = options(args, 1, "--course");
        String courseId = required(flags, "--course");
        if (args[0].equals("progress")) {
            output.println(facade.getProgress(courseId));
        } else {
            List<StudyEvent> events = facade.getHistory(courseId);
            if (events.isEmpty()) {
                output.println("No study history yet.");
            }
            for (StudyEvent event : events) {
                output.println(event);
            }
        }
        return 0;
    }

    private int quizCommand(String[] args) {
        if (args.length >= 2 && args[1].equals("create")) {
            return createQuizCommand(args);
        }
        if (args.length >= 2 && args[1].equals("list")) {
            String courseId = required(options(args, 2, "--course"), "--course");
            List<Quiz> quizzes = facade.listQuizzes(courseId);
            if (quizzes.isEmpty()) {
                output.println("No saved quizzes in this course.");
            }
            for (Quiz quiz : quizzes) {
                output.println(quiz.getId() + "  " + quiz.getTopic() + "  " + quiz.getDifficulty()
                        + "  questions=" + quiz.getQuestions().size());
            }
            return 0;
        }
        if (args.length >= 3 && args[1].equals("show")) {
            String courseId = required(options(args, 3, "--course"), "--course");
            output.println(facade.getQuiz(courseId, args[2]).describe());
            return 0;
        }
        if (args.length >= 2 && args[1].equals("attempts")) {
            String courseId = required(options(args, 2, "--course"), "--course");
            List<QuizAttempt> attempts = facade.listAttempts(courseId);
            if (attempts.isEmpty()) {
                output.println("No saved attempts in this course.");
            }
            for (QuizAttempt attempt : attempts) {
                output.println(attempt.getId() + "  " + attempt);
            }
            return 0;
        }
        if (args.length >= 3 && (args[1].equals("take") || args[1].equals("submit")
                || args[1].equals("attempt") || args[1].equals("regrade"))) {
            return quizAttemptCommand(args);
        }
        if (args.length >= 4 && args[1].equals("model-answer")) {
            return modelAnswerCommand(args);
        }
        if (args.length >= 5 && args[1].equals("selfgrade")) {
            return selfGradeCommand(args);
        }
        return unknownCommand();
    }

    private int quizAttemptCommand(String[] args) {
        Map<String, String> flags;
        if (args[1].equals("submit")) {
            flags = options(args, 3, "--course", "--answers");
        } else {
            flags = options(args, 3, "--course");
        }
        String courseId = required(flags, "--course");
        QuizAttempt attempt;
        if (args[1].equals("take")) {
            attempt = facade.startAttempt(courseId, args[2]);
        } else if (args[1].equals("submit")) {
            Map<String, String> answers = readAnswers(required(flags, "--answers"));
            attempt = facade.submitAttempt(courseId, args[2], answers);
        } else if (args[1].equals("regrade")) {
            int pending = facade.getAttempt(courseId, args[2]).getPendingCount();
            attempt = facade.retryPendingGrading(courseId, args[2]);
            if (pending == 0) {
                output.println("No pending answers to grade.");
            }
        } else {
            attempt = facade.getAttempt(courseId, args[2]);
        }
        Quiz quiz = facade.getQuiz(courseId, attempt.getQuizId());
        output.println(attempt.describe(quiz));
        if (!attempt.isSubmitted() || attempt.getPendingCount() > 0) {
            String label = "Question IDs for the answers file:";
            if (attempt.isSubmitted()) {
                label = "Pending question IDs:";
            }
            output.println(label);
            for (Question question : quiz.getQuestions()) {
                if (!attempt.isSubmitted() || attempt.getResult(question.getId()).getStatus() == GradingStatus.PENDING) {
                    output.println(question.getId() + "  " + question.getText());
                }
            }
        }
        return 0;
    }

    private int modelAnswerCommand(String[] args) {
        String courseId = required(options(args, 4, "--course"), "--course");
        ShortAnswerQuestion question = facade.getPendingQuestion(courseId, args[2], args[3]);
        QuizAttempt attempt = facade.getAttempt(courseId, args[2]);
        output.println("Question: " + question.getText());
        output.println("Your answer: " + attempt.getAnswers().get(question.getId()));
        output.println("Model answer: " + question.getCorrectAnswer());
        output.println("Rubric: " + question.getRubric());
        output.println("Compare your answer, then use quiz selfgrade with right or wrong.");
        return 0;
    }

    private int selfGradeCommand(String[] args) {
        String courseId = required(options(args, 5, "--course"), "--course");
        if (!args[4].equals("right") && !args[4].equals("wrong")) {
            throw new IllegalArgumentException("Self-grade must be right or wrong.");
        }
        QuizAttempt attempt = facade.selfGrade(courseId, args[2], args[3], args[4].equals("right"));
        output.println(attempt.describe(facade.getQuiz(courseId, attempt.getQuizId())));
        return 0;
    }

    private Map<String, String> readAnswers(String filePath) {
        try {
            return new ResponseParser().parseAnswers(Files.readString(Path.of(filePath)));
        } catch (IOException | JSONException e) {
            throw new IllegalArgumentException("The answers file must contain JSON with an answers object of text values.");
        }
    }

    private int createQuizCommand(String[] args) {
        Map<String, String> flags = options(args, 2, "--course", "--topic", "--count", "--difficulty", "--types");
        String courseId = required(flags, "--course");
        String topic = required(flags, "--topic");
        int count = Integer.parseInt(flags.getOrDefault("--count", "6"));
        Difficulty difficulty = Difficulty.parse(flags.getOrDefault("--difficulty", "medium"));
        List<QuestionType> types = new ArrayList<>();
        for (String type : flags.getOrDefault("--types", "multiple-choice,true-false,short-answer").split(",", -1)) {
            types.add(QuestionType.parse(type));
        }
        Quiz quiz = facade.createQuiz(courseId, topic, count, difficulty, types);
        output.println("Questions generated: " + quiz.getQuestions().size() + ". Requested: " + count + ".");
        output.println(quiz.describe());
        return 0;
    }

    private int unknownCommand() {
        errors.println("Unknown command. Run help to see available commands.");
        return 2;
    }

    private Map<String, String> options(String[] args, int start, String... allowed) {
        Map<String, String> values = new HashMap<>();
        // Each option name must be followed by one value.
        for (int i = start; i < args.length; i += 2) {
            String name = args[i];
            boolean known = false;
            for (String allowedName : allowed) {
                if (allowedName.equals(name)) {
                    known = true;
                    break;
                }
            }
            if (!known || i + 1 >= args.length) {
                throw new IllegalArgumentException("Unknown, repeated, or incomplete command option.");
            }
            String value = args[i + 1];
            if (value.startsWith("--") || values.containsKey(name)) {
                throw new IllegalArgumentException("Unknown, repeated, or incomplete command option.");
            }
            values.put(name, value);
        }
        return values;
    }

    private String required(Map<String, String> flags, String name) {
        if (!flags.containsKey(name) || flags.get(name).isBlank()) {
            throw new IllegalArgumentException("Required option: " + name);
        }
        return flags.get(name);
    }
}
