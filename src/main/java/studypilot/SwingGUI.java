package studypilot;

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

public class SwingGUI {
    private final AgentFacade facade;

    public SwingGUI(AgentFacade facade) {
        this.facade = facade;
    }

    public void showMainWindow() {
        JFrame window = new JFrame("StudyPilot");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JTabbedPane tabs = new JTabbedPane();
        String[] names = {"Courses and materials", "Questions", "Flashcards", "Quizzes",
                "Progress", "Study plans", "Assistant"};
        for (String name : names) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(new EmptyBorder(16, 16, 16, 16));
            panel.add(new JLabel(name + " - planned implementation"), BorderLayout.NORTH);
            tabs.addTab(name, panel);
        }
        JTextArea status = new JTextArea(facade.getImplementationStatus());
        status.setEditable(false);
        window.add(tabs, BorderLayout.CENTER);
        window.add(status, BorderLayout.SOUTH);
        window.setSize(1000, 700);
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }
}
