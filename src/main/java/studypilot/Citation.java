package studypilot;

public class Citation {
    private final String chunkId;
    private final String sourceFile;
    private final int page;
    private final int lineStart;
    private final int lineEnd;
    public Citation(Chunk chunk) {
        chunkId = chunk.getId();
        sourceFile = chunk.getSourceFile();
        page = chunk.getPage();
        lineStart = chunk.getLineStart();
        lineEnd = chunk.getLineEnd();
    }

    public String getChunkId() {
        return chunkId;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public int getPage() {
        return page;
    }

    public int getLineStart() {
        return lineStart;
    }

    public int getLineEnd() {
        return lineEnd;
    }

    public String toString() {
        String location;
        if (page > 0) {
            location = sourceFile + ", page " + page;
        } else {
            location = sourceFile + ", lines " + lineStart + "-" + lineEnd;
        }
        return location + " [" + chunkId + "]";
    }
}
