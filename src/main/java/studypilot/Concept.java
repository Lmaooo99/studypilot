package studypilot;

import java.util.ArrayList;
import java.util.List;

public class Concept {
    private final String name;
    private final String definition;
    private String parentName;
    private final List<String> sourceChunkIds;
    public Concept(String name, String definition, String parentName, List<String> sourceChunkIds) {
        this.name = name.trim();
        this.definition = definition.trim();
        this.parentName = parentName.trim();
        this.sourceChunkIds = new ArrayList<>(sourceChunkIds);
    }

    public String getName() {
        return name;
    }

    public String getDefinition() {
        return definition;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String name) {
        parentName = name;
    }

    public List<String> getSourceChunkIds() {
        return new ArrayList<>(sourceChunkIds);
    }

    public void addSource(String id) {
        if (!sourceChunkIds.contains(id)) {
            sourceChunkIds.add(id);
        }
    }

    public String toString() {
        String text = name + ": " + definition;
        if (!parentName.isEmpty()) {
            text += " (parent: " + parentName + ")";
        }
        return text + "\nSource chunks: " + sourceChunkIds;
    }
}
