package studypilot;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.EmptyBorder;

public class SwingGUI implements ActionListener {
    private final AgentFacade facade;
    private final JComboBox<Course> courses = new JComboBox<>();
    private final JComboBox<Material> materials = new JComboBox<>();
    private final JComboBox<Flashcard> cards = new JComboBox<>();
    private final JComboBox<String> styles = new JComboBox<>(new String[] {"brief", "detailed", "bullet"});
    private final JTextField courseName = new JTextField(20);
    private final JTextField question = new JTextField(60);
    private final JTextField cardCount = new JTextField("5", 4);
    private final JTextField quizTopic = new JTextField(24);
    private final JTextField quizCount = new JTextField("6", 4);
    private final JComboBox<String> difficulties = new JComboBox<>(new String[] {"Suggested", "EASY", "MEDIUM", "HARD"});
    private final JLabel difficultyReason = new JLabel("Enter a topic and choose Show suggestion to view its graded history.");
    private final JCheckBox multipleChoice = new JCheckBox("Multiple choice", true);
    private final JCheckBox trueFalse = new JCheckBox("True/false", true);
    private final JCheckBox shortAnswer = new JCheckBox("Short answer", true);
    private final JComboBox<Quiz> quizzes = new JComboBox<>();
    private final JComboBox<QuizAttempt> attempts = new JComboBox<>();
    private final JLabel attemptLabel = new JLabel("Start an attempt from a saved quiz.");
    private QuizAttempt currentAttempt;
    private Quiz currentQuiz;
    private String selfGradeQuestionId;
    private boolean selfGradeCorrect;
    private final List<String> answerQuestionIds = new ArrayList<>();
    private final DefaultTableModel answerRows = new DefaultTableModel(new String[] {"Question", "Your answer", "Status"}, 0) {
        public boolean isCellEditable(int row, int column) {
            return column == 1 && currentAttempt != null && !currentAttempt.isSubmitted();
        }
    };
    private final JTable attemptAnswers = new JTable(answerRows);
    private final JTextArea results = new JTextArea();
    private final JLabel status = new JLabel("Ready");
    private final List<JButton> buttons = new ArrayList<>();
    private JFrame window;
    private DashboardView dashboard;
    private boolean loading;
    private boolean busy;
    private boolean answerShown;

    public SwingGUI(AgentFacade facade) {
        this.facade = facade;
    }

    public void showMainWindow() {
        window = new JFrame("StudyPilot");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.add(selectionPanel(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(featureTabs(), BorderLayout.NORTH);
        results.setName("results");
        results.setEditable(false);
        results.setLineWrap(true);
        results.setWrapStyleWord(true);
        center.add(new JScrollPane(results), BorderLayout.CENTER);
        center.setBorder(new EmptyBorder(8, 8, 8, 8));
        window.add(center, BorderLayout.CENTER);
        window.add(status, BorderLayout.SOUTH);

        refreshCourses();
        results.setText(facade.getImplementationStatus() + "\n" + facade.getStorageWarnings());
        window.setSize(1150, 760);
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }

    private JPanel selectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        courses.setName("courses");
        materials.setName("materials");
        courses.addActionListener(this);
        panel.add(new JLabel("Course"));
        panel.add(courses);
        panel.add(new JLabel("Material"));
        panel.add(materials);
        return panel;
    }

    private JTabbedPane featureTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("features");
        tabs.addTab("Courses and materials", coursePanel());
        tabs.addTab("Questions", questionPanel());
        tabs.addTab("Flashcards", flashcardPanel());
        tabs.addTab("Quizzes", quizPanel());
        dashboard = new DashboardView(facade);
        facade.observeProgress(dashboard);
        tabs.addTab("Progress", progressPanel());
        tabs.addTab("Study plans", plannedPanel("Study plans"));
        tabs.addTab("Assistant", plannedPanel("Assistant"));
        return tabs;
    }

    private JPanel progressPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Weak topics", "weakTopics"));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(actions, BorderLayout.NORTH);
        panel.add(dashboard, BorderLayout.CENTER);
        return panel;
    }

    private JPanel coursePanel() {
        JPanel coursesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        courseName.setName("courseName");
        coursesPanel.add(new JLabel("New course"));
        coursesPanel.add(courseName);
        coursesPanel.add(button("Create course", "createCourse"));
        coursesPanel.add(button("Import material", "importMaterial"));

        JPanel analysisPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        analysisPanel.add(styles);
        analysisPanel.add(button("Summarize", "summarize"));
        analysisPanel.add(button("Extract concepts", "extractConcepts"));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(coursesPanel, BorderLayout.NORTH);
        panel.add(analysisPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel questionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        question.setName("question");
        panel.add(new JLabel("Question"));
        panel.add(question);
        panel.add(button("Ask with citations", "askQuestion"));
        return panel;
    }

    private JPanel flashcardPanel() {
        JPanel generation = new JPanel(new FlowLayout(FlowLayout.LEFT));
        generation.add(new JLabel("Number of cards"));
        generation.add(cardCount);
        generation.add(button("Generate flashcards", "generateFlashcards"));

        JPanel review = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cards.setName("cards");
        cards.addActionListener(this);
        review.add(cards);
        review.add(button("Flip card", "flipCard"));
        review.add(button("Remembered", "remembered"));
        review.add(button("Not yet", "notYet"));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(generation, BorderLayout.NORTH);
        panel.add(review, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel plannedPanel(String name) {
        JPanel panel = new JPanel();
        panel.add(new JLabel(name + " - planned implementation"));
        return panel;
    }

    private JPanel quizPanel() {
        JPanel generation = new JPanel(new FlowLayout(FlowLayout.LEFT));
        quizTopic.setName("quizTopic");
        quizCount.setName("quizCount");
        difficulties.setName("quizDifficulty");
        generation.add(new JLabel("Topic"));
        generation.add(quizTopic);
        generation.add(new JLabel("Questions"));
        generation.add(quizCount);
        generation.add(new JLabel("Difficulty"));
        generation.add(difficulties);
        generation.add(button("Show suggestion", "suggestDifficulty"));
        generation.add(button("Generate quiz", "createQuiz"));

        JPanel selection = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selection.add(multipleChoice);
        selection.add(trueFalse);
        selection.add(shortAnswer);
        quizzes.setName("quizzes");
        selection.add(new JLabel("Saved quiz"));
        selection.add(quizzes);
        selection.add(button("View quiz", "showQuiz"));
        selection.add(button("Start attempt", "startAttempt"));
        JPanel choices = new JPanel(new BorderLayout());
        choices.add(generation, BorderLayout.NORTH);
        choices.add(selection, BorderLayout.CENTER);
        choices.add(difficultyReason, BorderLayout.SOUTH);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(choices, BorderLayout.NORTH);
        panel.add(attemptPanel(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel attemptPanel() {
        JPanel saved = new JPanel(new FlowLayout(FlowLayout.LEFT));
        attempts.setName("attempts");
        saved.add(new JLabel("Saved attempt"));
        saved.add(attempts);
        saved.add(button("Open attempt", "openAttempt"));
        saved.add(attemptLabel);
        attemptAnswers.setName("attemptAnswers");
        attemptAnswers.setPreferredScrollableViewportSize(new Dimension(900, 90));
        attemptAnswers.getColumnModel().getColumn(0).setPreferredWidth(450);
        attemptAnswers.getColumnModel().getColumn(1).setPreferredWidth(350);
        attemptAnswers.getColumnModel().getColumn(2).setPreferredWidth(100);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Submit answers", "submitAttempt"));
        actions.add(button("Retry pending grading", "retryPendingGrading"));
        actions.add(button("Self-grade selected answer", "selfGrade"));
        JPanel submit = new JPanel(new BorderLayout());
        submit.add(actions, BorderLayout.NORTH);
        submit.add(new JLabel("Multiple choice: option number. True/false: true or false. Blank answers score zero."), BorderLayout.SOUTH);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(saved, BorderLayout.NORTH);
        panel.add(new JScrollPane(attemptAnswers), BorderLayout.CENTER);
        panel.add(submit, BorderLayout.SOUTH);
        return panel;
    }

    private Map<String, String> enteredAnswers() {
        if (attemptAnswers.isEditing()) {
            attemptAnswers.getCellEditor().stopCellEditing();
        }
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < answerQuestionIds.size(); i++) {
            Object value = answerRows.getValueAt(i, 1);
            String answer = "";
            if (value != null) {
                answer = value.toString();
            }
            answers.put(answerQuestionIds.get(i), answer);
        }
        return answers;
    }

    private void showAttempt(QuizAttempt attempt, Quiz quiz) {
        currentAttempt = attempt;
        currentQuiz = quiz;
        attemptLabel.setText("Attempt " + attempt.getId().substring(0, 8) + " - " + quiz.getTopic());
        answerRows.setRowCount(0);
        answerQuestionIds.clear();
        Map<String, String> answers = attempt.getAnswers();
        for (Question question : quiz.getQuestions()) {
            answerQuestionIds.add(question.getId());
            String gradingStatus = "Not submitted";
            if (attempt.isSubmitted()) {
                gradingStatus = attempt.getResult(question.getId()).getStatus().name();
            }
            answerRows.addRow(new Object[] {question.getText(), answers.getOrDefault(question.getId(), ""), gradingStatus});
        }
        for (int i = 0; i < attempts.getItemCount(); i++) {
            if (attempts.getItemAt(i).getId().equals(attempt.getId())) {
                attempts.setSelectedIndex(i);
                break;
            }
        }
    }

    private List<QuestionType> selectedQuestionTypes() {
        List<QuestionType> types = new ArrayList<>();
        if (multipleChoice.isSelected()) {
            types.add(QuestionType.MULTIPLE_CHOICE);
        }
        if (trueFalse.isSelected()) {
            types.add(QuestionType.TRUE_FALSE);
        }
        if (shortAnswer.isSelected()) {
            types.add(QuestionType.SHORT_ANSWER);
        }
        return types;
    }

    private void chooseSelfGrade() {
        checkSelection("selfGrade");
        int row = attemptAnswers.getSelectedRow();
        if (row < 0) {
            throw new IllegalArgumentException("Select a pending short-answer row first.");
        }
        String questionId = answerQuestionIds.get(row);
        Question question = currentQuiz.getQuestion(questionId);
        if (!(question instanceof ShortAnswerQuestion)
                || currentAttempt.getResult(questionId).getStatus() != GradingStatus.PENDING) {
            throw new IllegalArgumentException("Only pending short answers can be self-graded.");
        }
        String text = "Question: " + question.getText() + "\n\nYour answer: "
                + currentAttempt.getAnswers().get(questionId) + "\n\nModel answer: " + question.getCorrectAnswer()
                + "\n\nRubric: " + ((ShortAnswerQuestion) question).getRubric();
        JTextArea comparison = new JTextArea(text, 12, 60);
        comparison.setEditable(false);
        comparison.setLineWrap(true);
        comparison.setWrapStyleWord(true);
        String[] choices = {"Correct", "Incorrect", "Cancel"};
        int choice = JOptionPane.showOptionDialog(window, new JScrollPane(comparison), "Self-grade pending answer",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[2]);
        if (choice == 0 || choice == 1) {
            selfGradeQuestionId = questionId;
            selfGradeCorrect = choice == 0;
            runTask("selfGrade", null);
        }
    }

    private JButton button(String label, String action) {
        JButton button = new JButton(label);
        button.setName(action);
        button.setActionCommand(action);
        button.addActionListener(this);
        buttons.add(button);
        return button;
    }

    public void actionPerformed(ActionEvent event) {
        if (loading || busy) {
            return;
        }
        if (event.getSource() == courses) {
            refreshMaterials(null);
        } else if (event.getSource() == cards) {
            showCardFront();
        } else {
            try {
                String action = event.getActionCommand();
                if (action.equals("flipCard")) {
                    flipCard();
                } else if (action.equals("importMaterial")) {
                    chooseMaterial();
                } else if (action.equals("selfGrade")) {
                    chooseSelfGrade();
                } else {
                    runTask(action, null);
                }
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(window, e.getMessage());
            }
        }
    }

    private void showCardFront() {
        answerShown = false;
        Flashcard card = (Flashcard) cards.getSelectedItem();
        if (card != null) {
            results.setText("Front: " + card.getFront());
        }
    }

    private void flipCard() {
        Flashcard card = (Flashcard) cards.getSelectedItem();
        if (card == null) {
            results.setText("No flashcards to review.");
            return;
        }
        answerShown = true;
        results.setText("Front: " + card.getFront() + "\n\nBack: " + card.getBack());
    }

    private void chooseMaterial() {
        checkSelection("importMaterial");
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
            String filePath = chooser.getSelectedFile().getAbsolutePath();
            runTask("importMaterial", filePath);
        }
    }

    private void checkSelection(String action) {
        if (action.equals("createCourse")) {
            return;
        }
        if (courses.getSelectedItem() == null) {
            throw new IllegalArgumentException("Select a course first.");
        }
        if (action.equals("summarize") || action.equals("extractConcepts") || action.equals("generateFlashcards")) {
            if (materials.getSelectedItem() == null) {
                throw new IllegalArgumentException("Import and select a material first.");
            }
        }
        if (action.equals("remembered") || action.equals("notYet")) {
            if (cards.getSelectedItem() == null) {
                throw new IllegalArgumentException("No flashcards to review.");
            }
            if (!answerShown) {
                throw new IllegalArgumentException("Flip the card before recording a review.");
            }
        }
        if ((action.equals("showQuiz") || action.equals("startAttempt")) && quizzes.getSelectedItem() == null) {
            throw new IllegalArgumentException("Generate or select a saved quiz first.");
        }
        if (action.equals("openAttempt") && attempts.getSelectedItem() == null) {
            throw new IllegalArgumentException("There are no saved attempts in this course.");
        }
        if ((action.equals("submitAttempt") || action.equals("retryPendingGrading") || action.equals("selfGrade"))
                && currentAttempt == null) {
            throw new IllegalArgumentException("Start or open an attempt first.");
        }
        if ((action.equals("retryPendingGrading") || action.equals("selfGrade")) && !currentAttempt.isSubmitted()) {
            throw new IllegalArgumentException("Submit the attempt before resolving pending answers.");
        }
    }

    private void runTask(String action, String filePath) {
        checkSelection(action);
        StudyWorker worker = new StudyWorker(action, filePath);
        busy = true;
        setControlsEnabled(false);
        status.setText("Working...");
        worker.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        courses.setEnabled(enabled);
        materials.setEnabled(enabled);
        cards.setEnabled(enabled);
        courseName.setEnabled(enabled);
        question.setEnabled(enabled);
        cardCount.setEnabled(enabled);
        styles.setEnabled(enabled);
        quizTopic.setEnabled(enabled);
        quizCount.setEnabled(enabled);
        difficulties.setEnabled(enabled);
        multipleChoice.setEnabled(enabled);
        trueFalse.setEnabled(enabled);
        shortAnswer.setEnabled(enabled);
        quizzes.setEnabled(enabled);
        attempts.setEnabled(enabled);
        attemptAnswers.setEnabled(enabled);
        for (JButton button : buttons) {
            button.setEnabled(enabled);
        }
    }

    private void refreshCourses() {
        Course oldCourse = (Course) courses.getSelectedItem();
        Material oldMaterial = (Material) materials.getSelectedItem();
        loading = true;
        courses.removeAllItems();
        for (Course course : facade.listCourses()) {
            courses.addItem(course);
            if (oldCourse != null && oldCourse.getId().equals(course.getId())) {
                courses.setSelectedItem(course);
            }
        }
        loading = false;
        String materialId = null;
        if (oldMaterial != null) {
            materialId = oldMaterial.getId();
        }
        refreshMaterials(materialId);
    }

    private void refreshMaterials(String materialId) {
        loading = true;
        difficultyReason.setText("Enter a topic and choose Show suggestion to view its graded history.");
        answerShown = false;
        materials.removeAllItems();
        cards.removeAllItems();
        Quiz oldQuiz = (Quiz) quizzes.getSelectedItem();
        quizzes.removeAllItems();
        QuizAttempt oldAttempt = (QuizAttempt) attempts.getSelectedItem();
        attempts.removeAllItems();
        Course course = (Course) courses.getSelectedItem();
        if (currentAttempt != null && (course == null || !currentAttempt.getCourseId().equals(course.getId()))) {
            currentAttempt = null;
            currentQuiz = null;
            answerRows.setRowCount(0);
            answerQuestionIds.clear();
            attemptLabel.setText("Start an attempt from a saved quiz.");
        }
        if (course != null) {
            for (Material material : course.getMaterials()) {
                materials.addItem(material);
                if (material.getId().equals(materialId)) {
                    materials.setSelectedItem(material);
                }
            }
            for (Flashcard card : facade.getCardsToReview(course.getId())) {
                cards.addItem(card);
            }
            dashboard.setCourseId(course.getId());
            for (Quiz quiz : facade.listQuizzes(course.getId())) {
                quizzes.addItem(quiz);
                if (oldQuiz != null && oldQuiz.getId().equals(quiz.getId())) {
                    quizzes.setSelectedItem(quiz);
                }
            }
            for (QuizAttempt attempt : facade.listAttempts(course.getId())) {
                attempts.addItem(attempt);
                if (oldAttempt != null && oldAttempt.getId().equals(attempt.getId())) {
                    attempts.setSelectedItem(attempt);
                }
            }
        } else {
            dashboard.setCourseId(null);
        }
        loading = false;
    }

    // SwingWorker runs file and network operations without blocking the window.
    private class StudyWorker extends SwingWorker<String, Void> {
        private final String action;
        private final String filePath;
        private final Course course;
        private final Material material;
        private final Flashcard card;
        private final String name;
        private final String questionText;
        private final String style;
        private final String requestedCount;
        private final String topicText;
        private final String requestedQuizCount;
        private final String difficultyChoice;
        private DifficultySuggestion loadedSuggestion;
        private final List<QuestionType> questionTypes;
        private final Quiz selectedQuiz;
        private Quiz generatedQuiz;
        private final QuizAttempt selectedAttempt;
        private final String submittedAttemptId;
        private final Map<String, String> submittedAnswers;
        private QuizAttempt loadedAttempt;
        private Quiz attemptedQuiz;
        private final String questionToSelfGrade;
        private final boolean selfAssessment;

        public StudyWorker(String action, String filePath) {
            this.action = action;
            this.filePath = filePath;
            course = (Course) courses.getSelectedItem();
            material = (Material) materials.getSelectedItem();
            card = (Flashcard) cards.getSelectedItem();
            name = courseName.getText();
            questionText = question.getText();
            style = (String) styles.getSelectedItem();
            requestedCount = cardCount.getText();
            topicText = quizTopic.getText();
            requestedQuizCount = quizCount.getText();
            difficultyChoice = (String) difficulties.getSelectedItem();
            questionTypes = selectedQuestionTypes();
            selectedQuiz = (Quiz) quizzes.getSelectedItem();
            selectedAttempt = (QuizAttempt) attempts.getSelectedItem();
            submittedAttemptId = currentAttempt == null ? "" : currentAttempt.getId();
            submittedAnswers = enteredAnswers();
            questionToSelfGrade = selfGradeQuestionId;
            selfAssessment = selfGradeCorrect;
        }

        protected String doInBackground() {
            if (action.equals("createCourse")) {
                return createCourse();
            } else if (action.equals("importMaterial")) {
                return importMaterial();
            } else if (action.equals("summarize")) {
                return facade.summarize(course.getId(), material.getId(), style);
            } else if (action.equals("extractConcepts")) {
                return extractConcepts();
            } else if (action.equals("askQuestion")) {
                return facade.askQuestion(course.getId(), questionText).toString();
            } else if (action.equals("generateFlashcards")) {
                return generateFlashcards();
            } else if (action.equals("remembered") || action.equals("notYet")) {
                return reviewCard();
            } else if (action.equals("suggestDifficulty")) {
                loadedSuggestion = facade.suggestDifficulty(course.getId(), topicText);
                return loadedSuggestion.toString();
            } else if (action.equals("weakTopics")) {
                return facade.getWeakTopics(course.getId()).toString();
            } else if (action.equals("createQuiz")) {
                return createQuiz();
            } else if (action.equals("showQuiz")) {
                return facade.getQuiz(course.getId(), selectedQuiz.getId()).describe();
            } else if (action.equals("startAttempt") || action.equals("openAttempt") || action.equals("submitAttempt")
                    || action.equals("retryPendingGrading") || action.equals("selfGrade")) {
                return useAttempt();
            }
            throw new IllegalArgumentException("Unknown GUI action.");
        }

        private String createCourse() {
            Course created = facade.createCourse(name);
            return "Created course: " + created.getName() + "\nCourse ID: " + created.getId();
        }

        private String importMaterial() {
            Material imported = facade.importMaterial(course.getId(), filePath);
            StringBuilder text = new StringBuilder("Imported " + imported.getFileName());
            text.append("\nMaterial ID: " + imported.getId());
            text.append("\nChunks: " + imported.getChunks().size());
            for (Chunk chunk : imported.getChunks()) {
                text.append("\n" + chunk.getLocation());
            }
            return text.toString();
        }

        private String extractConcepts() {
            List<Concept> concepts = facade.extractConcepts(course.getId(), material.getId());
            StringBuilder text = new StringBuilder("Concept extraction results\n\n");
            if (concepts.isEmpty()) {
                text.append("No concepts found. Existing concepts were kept.");
            }
            for (Concept concept : concepts) {
                text.append(concept + "\n\n");
            }
            return text.toString();
        }

        private String generateFlashcards() {
            int count = Integer.parseInt(requestedCount);
            List<Flashcard> generated = facade.generateFlashcards(course.getId(), material.getId(), count);
            return "Generated " + generated.size() + " new cards; requested " + count + ". Select a card to review.";
        }

        private String reviewCard() {
            boolean remembered = action.equals("remembered");
            facade.reviewCard(course.getId(), card.getId(), remembered);
            Flashcard updated = facade.getCourse(course.getId()).getFlashcard(card.getId());
            return "Review recorded. Reviews: " + updated.getReviewCount() + ", mastery: " + updated.getMastery();
        }

        private String createQuiz() {
            int count = Integer.parseInt(requestedQuizCount);
            loadedSuggestion = facade.suggestDifficulty(course.getId(), topicText);
            Difficulty difficulty = loadedSuggestion.getLevel();
            if (!"Suggested".equals(difficultyChoice)) {
                difficulty = Difficulty.parse(difficultyChoice);
            }
            generatedQuiz = facade.createQuiz(course.getId(), topicText, count, difficulty, questionTypes);
            return loadedSuggestion + "\nSelected difficulty: " + difficulty
                    + "\nQuestions generated: " + generatedQuiz.getQuestions().size() + ". Requested: " + count
                    + ".\n\n" + generatedQuiz.describe();
        }

        private String useAttempt() {
            String notice = "";
            if (action.equals("startAttempt")) {
                loadedAttempt = facade.startAttempt(course.getId(), selectedQuiz.getId());
            } else if (action.equals("submitAttempt")) {
                loadedAttempt = facade.submitAttempt(course.getId(), submittedAttemptId, submittedAnswers);
            } else if (action.equals("retryPendingGrading")) {
                if (facade.getAttempt(course.getId(), submittedAttemptId).getPendingCount() == 0) {
                    notice = "No pending answers to grade.\n\n";
                }
                loadedAttempt = facade.retryPendingGrading(course.getId(), submittedAttemptId);
            } else if (action.equals("selfGrade")) {
                loadedAttempt = facade.selfGrade(course.getId(), submittedAttemptId, questionToSelfGrade, selfAssessment);
            } else {
                loadedAttempt = facade.getAttempt(course.getId(), selectedAttempt.getId());
            }
            attemptedQuiz = facade.getQuiz(course.getId(), loadedAttempt.getQuizId());
            return notice + loadedAttempt.describe(attemptedQuiz);
        }

        protected void done() {
            try {
                results.setText(get());
                refreshCourses();
                if (loadedSuggestion != null) {
                    difficultyReason.setText("Suggested for \"" + topicText.trim() + "\": "
                            + loadedSuggestion.getLevel() + "; " + loadedSuggestion.getReason());
                }
                if (generatedQuiz != null) {
                    selectGeneratedQuiz();
                }
                if (loadedAttempt != null) {
                    showAttempt(loadedAttempt, attemptedQuiz);
                }
                status.setText("Ready");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                status.setText("Operation interrupted.");
            } catch (ExecutionException e) {
                results.setText(e.getCause().getMessage());
                status.setText("Check the message above.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                results.setText(e.getMessage());
                status.setText("Check the message above.");
            } finally {
                busy = false;
                setControlsEnabled(true);
            }
        }

        private void selectGeneratedQuiz() {
            for (int i = 0; i < quizzes.getItemCount(); i++) {
                if (quizzes.getItemAt(i).getId().equals(generatedQuiz.getId())) {
                    quizzes.setSelectedIndex(i);
                    break;
                }
            }
        }
    }
}
