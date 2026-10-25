package studypilot;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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
