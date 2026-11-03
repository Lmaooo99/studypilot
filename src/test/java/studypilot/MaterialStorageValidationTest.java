package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class MaterialStorageValidationTest {
    private AppConfig config;
    private Course course;
    private Path saved;
    private String original;

    @Before
    public void setUp() throws Exception {
        Path workspace = Files.createTempDirectory("studypilot-material-fields-");
        config = new AppConfig(workspace);
        AgentFacade facade = new AgentFacade(config, new MockLLMClient());
        course = facade.createCourse("Design");
        Path file = workspace.resolve("notes.txt");
        Files.writeString(file, "Observer notifies subscribers.\nFacade has one entry point.");
        facade.importMaterial(course.getId(), file.toString());
        Path other = workspace.resolve("other.md");
        Files.writeString(other, "Strategy selects an algorithm.");
        facade.importMaterial(course.getId(), other.toString());
        saved = config.getDataDirectory().resolve("courses").resolve(course.getId() + ".json");
        original = Files.readString(saved);
    }

    private JSONObject firstChunk(JSONObject data) {
        return data.getJSONArray("materials").getJSONObject(0).getJSONArray("chunks").getJSONObject(0);
    }

    private void rejectsWithoutChangingTheFile(JSONObject data) throws Exception {
        String damaged = data.toString();
        Files.writeString(saved, damaged);
        try {
            new AgentFacade(config, new MockLLMClient()).getCourse(course.getId());
            fail("Invalid saved provenance must be reported without altering the file.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("original file was not changed"));
        }
        assertEquals(damaged, Files.readString(saved));
    }

    @Test
    public void rejectsFractionalAndStringSourceCoordinates() throws Exception {
        String[] fields = {"page", "lineStart", "lineEnd"};
        Object[] values = {"0", 1.5, "2"};
        for (int i = 0; i < fields.length; i++) {
            JSONObject data = new JSONObject(original);
            firstChunk(data).put(fields[i], values[i]);
            rejectsWithoutChangingTheFile(data);
        }
    }

    @Test
    public void rejectsAMixedPageAndLineLocation() throws Exception {
        JSONObject data = new JSONObject(original);
        firstChunk(data).put("page", 3);
        rejectsWithoutChangingTheFile(data);
    }

    @Test
    public void rejectsRepeatedChunkIdsAcrossMaterials() throws Exception {
        JSONObject data = new JSONObject(original);
        JSONObject second = data.getJSONArray("materials").getJSONObject(1).getJSONArray("chunks").getJSONObject(0);
        second.put("chunkId", firstChunk(data).getString("chunkId"));
        rejectsWithoutChangingTheFile(data);
    }

    @Test
    public void rejectsRepeatedMaterialIds() throws Exception {
        JSONObject data = new JSONObject(original);
        String id = data.getJSONArray("materials").getJSONObject(0).getString("materialId");
        data.getJSONArray("materials").getJSONObject(1).put("materialId", id);
        rejectsWithoutChangingTheFile(data);
    }

    @Test
    public void rejectsASourceFileNameThatDoesNotMatchItsMaterial() throws Exception {
        JSONObject data = new JSONObject(original);
        firstChunk(data).put("sourceFile", "different.pdf");
        rejectsWithoutChangingTheFile(data);
    }
}
