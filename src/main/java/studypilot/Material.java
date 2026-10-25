package studypilot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Material {
    private final String materialId;
    private final String fileName;
    private final String contentHash;
    private String summary;
    private final List<Chunk> chunks;

    public Material(String fileName, String contentHash, List<Chunk> chunks) {
        this(UUID.randomUUID().toString(), fileName, contentHash, "", chunks);
    }

    public Material(String materialId, String fileName, String contentHash, String summary, List<Chunk> chunks) {
        this.materialId = materialId;
        this.fileName = fileName;
        this.contentHash = contentHash;
        this.summary = summary;
        this.chunks = new ArrayList<>(chunks);
    }

    public String getId() {
        return materialId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<Chunk> getChunks() {
        return new ArrayList<>(chunks);
    }

    public String toString() {
        return fileName;
    }
}
