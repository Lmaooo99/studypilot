package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CourseServiceTest {
    private Path directory;
    private CourseService service;
    private Course course;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("studypilot-material-test-");
        service = new CourseService(new CourseRepository(directory.resolve("data")));
        course = service.createCourse("Software Design");
    }

    @Test
    public void importsTextAndPreservesIdsAfterReload() throws Exception {
        Path file = directory.resolve("lecture.txt");
        Files.writeString(file, "Encapsulation hides state.\nInterfaces describe behavior.");
        Material material = service.importMaterial(course.getId(), file.toString());
        Course saved = new CourseRepository(directory.resolve("data")).loadCourse(course.getId());
        assertEquals(material.getId(), saved.getMaterials().get(0).getId());
        Chunk chunk = saved.getChunks().get(0);
        assertEquals(material.getChunks().get(0).getId(), chunk.getId());
        assertEquals(1, chunk.getLineStart());
        assertEquals(2, chunk.getLineEnd());
        assertEquals(0, chunk.getPage());
    }

    @Test
    public void startsAChunkOnItsFirstContentLine() throws Exception {
        Path file = directory.resolve("overlap-newline.md");
        Files.writeString(file, "a".repeat(700) + "\n" + "b".repeat(99) + "\n" + "c".repeat(50) + "\n");
        Material material = service.importMaterial(course.getId(), file.toString());
        assertEquals(2, material.getChunks().size());
        Chunk second = material.getChunks().get(1);
        assertEquals(2, second.getLineStart());
        assertEquals(3, second.getLineEnd());
        assertEquals("\n" + "b".repeat(99) + "\n" + "c".repeat(50) + "\n", second.getText());
    }

    @Test
    public void doesNotCountAFinalNewlineAsAnExtraLine() throws Exception {
        Path file = directory.resolve("two-lines.md");
        Files.writeString(file, "First line.\nSecond line.\n");
        Material material = service.importMaterial(course.getId(), file.toString());
        assertEquals(1, material.getChunks().get(0).getLineStart());
        assertEquals(2, material.getChunks().get(0).getLineEnd());
    }

    @Test
    public void skipsAnUnreadableCourseFileAndListsTheOthers() throws Exception {
        Path copy = directory.resolve("data").resolve("courses").resolve(course.getId() + " copy.json");
        Files.writeString(copy, "{\"broken\": ");
        CourseRepository repository = new CourseRepository(directory.resolve("data"));
        List<Course> listed = repository.listCourses();
        assertEquals(1, listed.size());
        assertEquals(course.getId(), listed.get(0).getId());
        assertEquals("courses/" + course.getId() + " copy.json", repository.getSkippedFiles().get(0));
        assertEquals("{\"broken\": ", Files.readString(copy));
    }

    @Test
    public void chunksLongLinesWithThePromisedOverlap() throws Exception {
        Path file = directory.resolve("notes.md");
        String text = "abcdefghij".repeat(150);
        Files.writeString(file, text);
        Material material = service.importMaterial(course.getId(), file.toString());
        assertEquals(2, material.getChunks().size());
        assertEquals(text.substring(700), material.getChunks().get(1).getText());
        assertEquals(1, material.getChunks().get(1).getLineStart());
    }

    @Test
    public void rejectsDuplicateContentWithoutAddingAnotherMaterial() throws Exception {
        Path file = directory.resolve("notes.txt");
        Files.writeString(file, "An observer receives subject updates.");
        service.importMaterial(course.getId(), file.toString());
        try {
            service.importMaterial(course.getId(), file.toString());
            fail("Duplicate content must be skipped.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Duplicate material skipped"));
        }
        assertEquals(1, service.getCourse(course.getId()).getMaterials().size());
    }

    @Test
    public void rejectsEmptyAndUnsupportedFilesWithoutChangingTheCourse() throws Exception {
        for (String name : new String[] {"empty.txt", "image.png"}) {
            Path file = directory.resolve(name);
            Files.writeString(file, "  ");
            try {
                service.importMaterial(course.getId(), file.toString());
                fail("Invalid material must be rejected.");
            } catch (IllegalArgumentException e) {
                assertFalse(e.getMessage().isBlank());
            }
        }
        assertEquals(0, service.getCourse(course.getId()).getMaterials().size());
    }

    @Test
    public void rejectsTraversalInSavedCourseIds() {
        try {
            service.getCourse("../../.env");
            fail("A path must not be accepted as an ID.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid"));
        }
    }

    @Test
    public void preservesSupplementaryCharactersAtChunkAndOverlapBoundaries() throws Exception {
        for (int position : new int[] {699, 799}) {
            Path file = directory.resolve("unicode-" + position + ".txt");
            String symbol = "\uD83D\uDE00";
            Files.writeString(file, "a".repeat(position) + symbol + "b".repeat(800));
            Material material = service.importMaterial(course.getId(), file.toString());
            boolean preserved = false;
            for (Chunk chunk : service.getCourse(course.getId()).getMaterial(material.getId()).getChunks()) {
                String text = chunk.getText();
                assertFalse(Character.isLowSurrogate(text.charAt(0)));
                assertFalse(Character.isHighSurrogate(text.charAt(text.length() - 1)));
                if (text.contains(symbol)) {
                    preserved = true;
                }
            }
            assertTrue(preserved);
        }
    }

    @Test
    public void rejectsAMaterialContainingOnlyAnEncodingMarker() throws Exception {
        Path file = directory.resolve("bom-only.txt");
        Files.writeString(file, "\uFEFF");
        try {
            service.importMaterial(course.getId(), file.toString());
            fail("An encoding marker is not study material.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("no readable text"));
        }
        assertEquals(0, service.getCourse(course.getId()).getMaterials().size());
    }
}
