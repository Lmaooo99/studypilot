package studypilot;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    String[] getRequiredArguments();
    String[] getOptionalArguments();
    ToolResult execute(Map<String, String> arguments);
}
