package studypilot;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
    private final JComboBox<String> styles = new JComboBox<>(new String[] {"brief", "detailed", "bullet"});
    private final JTextField courseName = new JTextField(20);
    private final JButton createButton = new JButton("Create course");
    private final JButton importButton = new JButton("Import material");
    private final JButton summarizeButton = new JButton("Summarize");
    private final JButton extractButton = new JButton("Extract concepts");
    private final JTextArea results = new JTextArea();
    private final JLabel status = new JLabel("Ready");
    private JFrame window;
    private boolean loading;
    private boolean busy;

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
        results.setText(facade.getImplementationStatus());
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
        String[] planned = {"Questions", "Flashcards", "Quizzes", "Progress", "Study plans", "Assistant"};
        for (String name : planned) {
            JPanel panel = new JPanel();
            panel.add(new JLabel(name + " - planned implementation"));
            tabs.addTab(name, panel);
        }
        return tabs;
    }

    private JPanel coursePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        courseName.setName("courseName");
        createButton.setName("createCourse");
        importButton.setName("importMaterial");
        summarizeButton.setName("summarize");
        extractButton.setName("extractConcepts");
        createButton.addActionListener(this);
        importButton.addActionListener(this);
        summarizeButton.addActionListener(this);
        extractButton.addActionListener(this);
        panel.add(new JLabel("New course"));
        panel.add(courseName);
        panel.add(createButton);
        panel.add(importButton);
        panel.add(styles);
        panel.add(summarizeButton);
        panel.add(extractButton);
        return panel;
    }

    public void actionPerformed(ActionEvent event) {
        if (loading || busy) {
            return;
        }
        try {
            if (event.getSource() == courses) {
                refreshMaterials(null);
            } else if (event.getSource() == createButton) {
                runTask("createCourse", null);
            } else if (event.getSource() == importButton) {
                chooseMaterial();
            } else if (event.getSource() == summarizeButton) {
                checkMaterialSelection();
                runTask("summarize", null);
            } else if (event.getSource() == extractButton) {
                checkMaterialSelection();
                runTask("extractConcepts", null);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            JOptionPane.showMessageDialog(window, e.getMessage());
        }
    }

    private void checkMaterialSelection() {
        if (courses.getSelectedItem() == null || materials.getSelectedItem() == null) {
            throw new IllegalArgumentException("Select a course and material first.");
        }
    }

    private void chooseMaterial() {
        if (courses.getSelectedItem() == null) {
            throw new IllegalArgumentException("Create or select a course first.");
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
            String filePath = chooser.getSelectedFile().getAbsolutePath();
            runTask("importMaterial", filePath);
        }
    }

    private void runTask(String action, String filePath) {
        StudyWorker worker = new StudyWorker(action, filePath);
        busy = true;
        setControlsEnabled(false);
        status.setText("Working...");
        worker.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        courses.setEnabled(enabled);
        materials.setEnabled(enabled);
        courseName.setEnabled(enabled);
        createButton.setEnabled(enabled);
        importButton.setEnabled(enabled);
        styles.setEnabled(enabled);
        summarizeButton.setEnabled(enabled);
        extractButton.setEnabled(enabled);
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
        materials.removeAllItems();
        Course course = (Course) courses.getSelectedItem();
        if (course != null) {
            for (Material material : course.getMaterials()) {
                materials.addItem(material);
                if (material.getId().equals(materialId)) {
                    materials.setSelectedItem(material);
                }
            }
        }
        loading = false;
    }

    // File and LLM operations run without blocking the window.
    private class StudyWorker extends SwingWorker<String, Void> {
        private final String action;
        private final String filePath;
        private final String name;
        private final Course course;
        private final Material material;
        private final String style;

        public StudyWorker(String action, String filePath) {
            this.action = action;
            this.filePath = filePath;
            name = courseName.getText();
            course = (Course) courses.getSelectedItem();
            material = (Material) materials.getSelectedItem();
            style = (String) styles.getSelectedItem();
        }

        protected String doInBackground() {
            if (action.equals("createCourse")) {
                Course created = facade.createCourse(name);
                return "Created course: " + created.getName() + "\nCourse ID: " + created.getId();
            }
            if (action.equals("importMaterial")) {
                Material imported = facade.importMaterial(course.getId(), filePath);
                StringBuilder text = new StringBuilder("Imported " + imported.getFileName());
                text.append("\nMaterial ID: " + imported.getId());
                text.append("\nChunks: " + imported.getChunks().size());
                for (Chunk chunk : imported.getChunks()) {
                    text.append("\n" + chunk.getLocation());
                }
                return text.toString();
            }
            if (action.equals("summarize")) {
                return facade.summarize(course.getId(), material.getId(), style);
            }
            if (action.equals("extractConcepts")) {
                return extractConcepts();
            }
            throw new IllegalArgumentException("Unknown GUI action.");
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
