package studypilot;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class DashboardView extends JPanel implements Observer {
    private final AgentFacade facade;
    private final JLabel counts = new JLabel("Select a course to view progress.");
    private final JTextArea history = new JTextArea(8, 60);
    private String courseId;

    public DashboardView(AgentFacade facade) {
        super(new BorderLayout());
        this.facade = facade;
        history.setEditable(false);
        history.setName("studyHistory");
        counts.setName("reviewCount");
        add(counts, BorderLayout.NORTH);
        add(new JScrollPane(history), BorderLayout.CENTER);
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
        refresh();
    }

    public void refresh() {
        if (courseId == null) {
            counts.setText("Select a course to view progress.");
            history.setText("");
            return;
        }
        counts.setText(facade.getProgress(courseId).toString());
        StringBuilder text = new StringBuilder();
        for (StudyEvent event : facade.getHistory(courseId)) {
            text.append(event + "\n");
        }
        if (text.length() == 0) {
            text.append("No study history yet.");
        }
        history.setText(text.toString());
    }

    public void update(Subject subject) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                refresh();
            }
        });
    }
}
