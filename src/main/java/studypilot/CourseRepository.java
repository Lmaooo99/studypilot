package studypilot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

            // Earlier course files may not have concepts yet.
            if (data.has("concepts")) {
                List<Concept> concepts = new ArrayList<>();
                JSONArray savedConcepts = data.getJSONArray("concepts");
                for (int i = 0; i < savedConcepts.length(); i++) {
                    concepts.add(conceptFromJson(savedConcepts.getJSONObject(i)));
                }
                course.setConcepts(concepts);
            }
            return course;
        } catch (JSONException e) {
            throw new IllegalStateException("Saved course fields are invalid. The original file was not changed.");
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
        JSONArray savedChunks = data.getJSONArray("chunks");
        for (int i = 0; i < savedChunks.length(); i++) {
            chunks.add(chunkFromJson(savedChunks.getJSONObject(i)));
        }
        String id = data.getString("materialId");
        String fileName = data.getString("fileName");
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
        int page = data.getInt("page");
        int lineStart = data.getInt("lineStart");
        int lineEnd = data.getInt("lineEnd");
        return new Chunk(id, text, fileName, page, lineStart, lineEnd);
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
