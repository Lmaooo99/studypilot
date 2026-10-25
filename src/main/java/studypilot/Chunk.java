package studypilot;

import java.util.UUID;

public class Chunk {
    private final String chunkId;
    private final String text;
    private final String sourceFile;
    private final int page;
    private final int lineStart;
    private final int lineEnd;

    public Chunk(String text, String sourceFile, int page, int lineStart, int lineEnd) {
        this(UUID.randomUUID().toString(), text, sourceFile, page, lineStart, lineEnd);
    }

    public Chunk(String chunkId, String text, String sourceFile, int page, int lineStart, int lineEnd) {
        this.chunkId = chunkId;
        this.text = text;
        this.sourceFile = sourceFile;
        this.page = page;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
    }

    public String getId() {
        return chunkId;
    }

    public String getText() {
        return text;
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

    public String getLocation() {
        if (page > 0) {
            return sourceFile + ", page " + page;
        }
        return sourceFile + ", lines " + lineStart + "-" + lineEnd;
    }
}
