package studypilot;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
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
import javax.swing.SwingWorker;
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
        tabs.addTab("Quizzes", plannedPanel("Quizzes"));
        dashboard = new DashboardView(facade);
        facade.observeProgress(dashboard);
        tabs.addTab("Progress", dashboard);
        tabs.addTab("Study plans", plannedPanel("Study plans"));
        tabs.addTab("Assistant", plannedPanel("Assistant"));
        return tabs;
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
        answerShown = false;
        materials.removeAllItems();
        cards.removeAllItems();
        Course course = (Course) courses.getSelectedItem();
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

        protected void done() {
            try {
                results.setText(get());
                refreshCourses();
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
    }
}
