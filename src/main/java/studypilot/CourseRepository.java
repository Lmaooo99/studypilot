package studypilot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CourseRepository {
    private final Path directory;

    public CourseRepository(Path dataDirectory) {
        directory = dataDirectory.resolve("courses");
    }

    public void saveCourse(Course course) {
        JSONObject data = new JSONObject();
        data.put("courseId", course.getId());
        data.put("name", course.getName());

        JSONArray materials = new JSONArray();
        for (Material material : course.getMaterials()) {
            materials.put(materialToJson(material));
        }
        data.put("materials", materials);

        JSONArray concepts = new JSONArray();
        for (Concept concept : course.getConcepts()) {
            concepts.put(conceptToJson(concept));
        }
        data.put("concepts", concepts);

        JSONArray cards = new JSONArray();
        for (Flashcard card : course.getFlashcards()) {
            cards.put(flashcardToJson(card));
        }
        data.put("flashcards", cards);

        data.put("reviewEvents", reviewEventsToJson(course.getReviewEvents()));

        Path file = JsonFiles.namedFile(directory, course.getId());
        JsonFiles.write(file, data);
    }

    public Course loadCourse(String courseId) {
        Path file = JsonFiles.namedFile(directory, courseId);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Course not found.");
        }
        JSONObject data = JsonFiles.read(file);
        try {
            if (!data.getString("courseId").equals(courseId)) {
                throw new JSONException("ID mismatch");
            }

            List<Material> materials = new ArrayList<>();
            JSONArray savedMaterials = data.getJSONArray("materials");
            for (int i = 0; i < savedMaterials.length(); i++) {
                materials.add(materialFromJson(savedMaterials.getJSONObject(i)));
            }
            Course course = new Course(courseId, data.getString("name"), materials);
            checkMaterialIds(course);

            // Earlier course files may not have concepts or flashcards yet.
            if (data.has("concepts")) {
                List<Concept> concepts = new ArrayList<>();
                JSONArray savedConcepts = data.getJSONArray("concepts");
                for (int i = 0; i < savedConcepts.length(); i++) {
                    concepts.add(conceptFromJson(savedConcepts.getJSONObject(i)));
                }
                course.setConcepts(concepts);
            }
            if (data.has("flashcards")) {
                List<Flashcard> cards = new ArrayList<>();
                JSONArray savedCards = data.getJSONArray("flashcards");
                for (int i = 0; i < savedCards.length(); i++) {
                    cards.add(flashcardFromJson(savedCards.getJSONObject(i)));
                }
                course.setFlashcards(cards);
            }
            loadReviewEvents(data, course);
            return course;
        } catch (JSONException | IllegalArgumentException | DateTimeException e) {
            throw new IllegalStateException("Saved course fields are invalid. The original file was not changed.");
        }
    }

    private void checkMaterialIds(Course course) {
        List<String> materialIds = new ArrayList<>();
        List<String> chunkIds = new ArrayList<>();
        for (Material material : course.getMaterials()) {
            if (material.getId().isBlank() || materialIds.contains(material.getId())) {
                throw new JSONException("Repeated or empty material ID");
            }
            materialIds.add(material.getId());
            for (Chunk chunk : material.getChunks()) {
                if (chunk.getId().isBlank() || chunkIds.contains(chunk.getId())) {
                    throw new JSONException("Repeated or empty chunk ID");
                }
                chunkIds.add(chunk.getId());
            }
        }
    }

    private JSONArray reviewEventsToJson(List<StudyEvent> events) {
        JSONArray reviews = new JSONArray();
        for (StudyEvent event : events) {
            JSONObject item = new JSONObject();
            item.put("eventId", event.getId());
            item.put("time", event.getTime().toString());
            item.put("conceptName", event.getConceptName());
            item.put("description", event.getDescription());
            reviews.put(item);
        }
        return reviews;
    }

    private void loadReviewEvents(JSONObject data, Course course) {
        // Older files retain their card counts but have no detailed review events.
        if (!data.has("reviewEvents")) {
            return;
        }
        JSONArray reviews = data.getJSONArray("reviewEvents");
        for (int i = 0; i < reviews.length(); i++) {
            JSONObject item = reviews.getJSONObject(i);
            course.addReviewEvent(new StudyEvent(item.getString("eventId"),
                    LocalDateTime.parse(item.getString("time")), course.getId(),
                    item.getString("conceptName"), item.getString("description")));
        }
        if (course.getReviewEvents().size() > course.getTotalReviews()) {
            throw new JSONException("More review events than saved reviews");
        }
    }

    private JSONObject materialToJson(Material material) {
        JSONObject data = new JSONObject();
        data.put("materialId", material.getId());
        data.put("fileName", material.getFileName());
        data.put("contentHash", material.getContentHash());
        data.put("summary", material.getSummary());

        JSONArray chunks = new JSONArray();
        for (Chunk chunk : material.getChunks()) {
            chunks.put(chunkToJson(chunk));
        }
        data.put("chunks", chunks);
        return data;
    }

    private Material materialFromJson(JSONObject data) {
        List<Chunk> chunks = new ArrayList<>();
        String fileName = data.getString("fileName");
        JSONArray savedChunks = data.getJSONArray("chunks");
        for (int i = 0; i < savedChunks.length(); i++) {
            Chunk chunk = chunkFromJson(savedChunks.getJSONObject(i));
            if (!chunk.getSourceFile().equals(fileName)) {
                throw new JSONException("Chunk source file does not match its material");
            }
            chunks.add(chunk);
        }
        String id = data.getString("materialId");
        String hash = data.getString("contentHash");
        String summary = data.getString("summary");
        return new Material(id, fileName, hash, summary, chunks);
    }

    private JSONObject chunkToJson(Chunk chunk) {
        JSONObject data = new JSONObject();
        data.put("chunkId", chunk.getId());
        data.put("text", chunk.getText());
        data.put("sourceFile", chunk.getSourceFile());
        data.put("page", chunk.getPage());
        data.put("lineStart", chunk.getLineStart());
        data.put("lineEnd", chunk.getLineEnd());
        return data;
    }

    private Chunk chunkFromJson(JSONObject data) {
        String id = data.getString("chunkId");
        String text = data.getString("text");
        String fileName = data.getString("sourceFile");
        int page = wholeNumber(data, "page");
        int lineStart = wholeNumber(data, "lineStart");
        int lineEnd = wholeNumber(data, "lineEnd");
        if (page < 0 || lineStart < 0 || lineEnd < lineStart
                || (page == 0 && lineStart == 0) || (page > 0 && (lineStart != 0 || lineEnd != 0))) {
            throw new JSONException("Invalid saved page or line range");
        }
        return new Chunk(id, text, fileName, page, lineStart, lineEnd);
    }

    private int wholeNumber(JSONObject data, String name) {
        if (!(data.get(name) instanceof Integer)) {
            throw new JSONException("Saved counts and locations must be whole numbers");
        }
        return data.getInt(name);
    }

    private JSONObject conceptToJson(Concept concept) {
        JSONObject data = new JSONObject();
        data.put("name", concept.getName());
        data.put("definition", concept.getDefinition());
        data.put("parentName", concept.getParentName());
        data.put("sourceChunkIds", new JSONArray(concept.getSourceChunkIds()));
        return data;
    }

    private Concept conceptFromJson(JSONObject data) {
        List<String> sourceIds = new ArrayList<>();
        JSONArray savedIds = data.getJSONArray("sourceChunkIds");
        for (int i = 0; i < savedIds.length(); i++) {
            sourceIds.add(savedIds.getString(i));
        }
        String name = data.getString("name");
        String definition = data.getString("definition");
        String parentName = data.getString("parentName");
        return new Concept(name, definition, parentName, sourceIds);
    }

    private JSONObject flashcardToJson(Flashcard card) {
        JSONObject data = new JSONObject();
        data.put("cardId", card.getId());
        data.put("front", card.getFront());
        data.put("back", card.getBack());
        data.put("conceptName", card.getConceptName());
        data.put("reviewCount", card.getReviewCount());
        data.put("rememberedCount", card.getRememberedCount());
        return data;
    }

    private Flashcard flashcardFromJson(JSONObject data) {
        String id = data.getString("cardId");
        String front = data.getString("front");
        String back = data.getString("back");
        String conceptName = data.getString("conceptName");
        int reviewCount = wholeNumber(data, "reviewCount");
        int rememberedCount = wholeNumber(data, "rememberedCount");
        return new Flashcard(id, front, back, conceptName, reviewCount, rememberedCount);
    }

    public List<Course> listCourses() {
        List<Course> courses = new ArrayList<>();
        if (!Files.exists(directory)) {
            return courses;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : entries) {
                files.add(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("The course directory could not be read.");
        }
        Collections.sort(files);
        for (Path file : files) {
            String name = file.getFileName().toString();
            String courseId = name.substring(0, name.length() - 5);
            courses.add(loadCourse(courseId));
        }
        return courses;
    }
}
