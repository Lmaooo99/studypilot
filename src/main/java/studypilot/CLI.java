package studypilot;

import java.io.PrintStream;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

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
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            output.println("StudyPilot");
            output.println("  gui     Open the graphical interface");
            output.println("  status  Show implementation status");
            output.println("  help    Show this help");
            return 0;
        }
        if (args[0].equals("status")) {
            output.println(facade.getImplementationStatus());
            return 0;
        }
        errors.println("Unknown command. Run help to see available commands.");
        return 2;
    }
}
