package studypilot;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PdfImportTest {
    private Path directory;
    private CourseService service;
    private Course course;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("studypilot-pdf-test-");
        service = new CourseService(new CourseRepository(directory.resolve("data")));
        course = service.createCourse("Design");
    }

    @Test
    public void keepsEachPdfPageSeparateAfterSavingAndReloading() throws Exception {
        Path file = directory.resolve("lecture.pdf");
        try (PDDocument document = new PDDocument()) {
            for (int n = 1; n <= 2; n++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(40, 700);
                    stream.showText("Material on page " + n);
                    stream.endText();
                }
            }
            document.save(file.toFile());
        }
        service.importMaterial(course.getId(), file.toString());
        Course saved = service.getCourse(course.getId());
        assertEquals(2, saved.getChunks().size());
        assertEquals(1, saved.getChunks().get(0).getPage());
        assertEquals(2, saved.getChunks().get(1).getPage());
        assertTrue(saved.getChunks().get(1).getText().contains("page 2"));
        assertEquals(0, saved.getChunks().get(0).getLineStart());
    }

    @Test
    public void rejectsCorruptAndImageOnlyPdfsWithoutSavingMaterial() throws Exception {
        Path corrupt = directory.resolve("corrupt.pdf");
        Files.writeString(corrupt, "This is not a PDF.");
        Path blank = directory.resolve("blank.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(blank.toFile());
        }
        for (Path file : new Path[] {corrupt, blank}) {
            try {
                service.importMaterial(course.getId(), file.toString());
                fail("Unreadable PDF must be rejected.");
            } catch (IllegalArgumentException e) {
                assertFalse(e.getMessage().isBlank());
            }
        }
        assertEquals(0, service.getCourse(course.getId()).getMaterials().size());
    }

    @Test
    public void rejectsFilesAboveTheLimitBeforeReadingTheirContents() throws Exception {
        Path file = directory.resolve("large.pdf");
        try (RandomAccessFile sparse = new RandomAccessFile(file.toFile(), "rw")) {
            sparse.setLength(20L * 1024 * 1024 + 1);
        }
        try {
            service.importMaterial(course.getId(), file.toString());
            fail("Large files must be rejected.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("20 MiB"));
        }
    }
}
