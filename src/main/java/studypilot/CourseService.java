package studypilot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class CourseService {
    private final CourseRepository repository;
    private static final int CHUNK_SIZE = 800;
    private static final int OVERLAP = 100;
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    public CourseService(CourseRepository repository) {
        this.repository = repository;
    }

    public Course createCourse(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Enter a course name.");
        }
        Course course = new Course(name.trim());
        repository.saveCourse(course);
        return course;
    }
    public Course getCourse(String courseId) {
        return repository.loadCourse(courseId);
    }

    public List<Course> listCourses() {
        return repository.listCourses();
    }

    public Material importMaterial(String courseId, String filePath) {
        Course course = getCourse(courseId);
        Path file = Path.of(filePath);
        try {
            checkMaterialFile(file);
            String fileName = file.getFileName().toString();
            String hash = contentHash(Files.readAllBytes(file));
            checkDuplicateMaterial(course, hash);

            List<Chunk> chunks;
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                chunks = extractPdf(file, fileName);
            } else {
                String text = readTextFile(file);
                chunks = splitIntoChunks(text, fileName, 0);
            }
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("The material has no readable text.");
            }

            Material material = new Material(fileName, hash, chunks);
            course.addMaterial(material);
            repository.saveCourse(course);
            return material;
        } catch (IOException e) {
            throw new IllegalArgumentException("The material could not be read. Check the file format and permissions.");
        }
    }

    private void checkMaterialFile(Path file) throws IOException {
        if (file.getFileName() == null) {
            throw new IllegalArgumentException("Select a material file.");
        }
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".txt") && !fileName.endsWith(".md") && !fileName.endsWith(".pdf")) {
            throw new IllegalArgumentException("Supported material types are .txt, .md and .pdf.");
        }
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Select a readable file no larger than 20 MiB.");
        }
    }

    private void checkDuplicateMaterial(Course course, String hash) {
        for (Material material : course.getMaterials()) {
            if (material.getContentHash().equals(hash)) {
                throw new IllegalArgumentException("Duplicate material skipped. Existing material ID: " + material.getId());
            }
        }
    }

    private String readTextFile(Path file) throws IOException {
        String text = Files.readString(file);
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text;
    }

    private List<Chunk> extractPdf(Path file, String fileName) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw new IllegalArgumentException("This PDF does not permit text extraction.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                chunks.addAll(splitIntoChunks(text, fileName, page));
            }
        }
        return chunks;
    }

    private String contentHash(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hashText = new StringBuilder();
            for (byte value : hash) {
                hashText.append(String.format("%02x", value & 255));
            }
            return hashText.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.");
        }
    }

    private List<Chunk> splitIntoChunks(String text, String fileName, int page) {
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int line = 1;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            // Keep UTF-16 surrogate pairs together at both chunk boundaries.
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                    && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }
            String part = text.substring(start, end);
            int lastLine = line + countNewlines(text, start, end);
            if (!part.isBlank()) {
                if (page == 0) {
                    chunks.add(new Chunk(part, fileName, 0, line, lastLine));
                } else {
                    chunks.add(new Chunk(part, fileName, page, 0, 0));
                }
            }
            if (end == text.length()) {
                break;
            }
            int next = end - OVERLAP;
            if (Character.isLowSurrogate(text.charAt(next)) && next > start
                    && Character.isHighSurrogate(text.charAt(next - 1))) {
                next--;
            }
            line += countNewlines(text, start, next);
            start = next;
        }
        return chunks;
    }

    private int countNewlines(String text, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
}
