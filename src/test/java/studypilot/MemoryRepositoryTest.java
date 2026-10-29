package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class MemoryRepositoryTest {
    @Test
    public void preservesRolesTextAndSessionIsolationAfterReload() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-memory-test-");
        MemoryRepository repository = new MemoryRepository(directory);
        String session = UUID.randomUUID().toString();
        repository.saveConversation("course", session, Arrays.asList(
                new Message("user", "Explain \"Observer\".\nUse the notes."), new Message("assistant", "The subject notifies observers.")));
        List<Message> restored = new MemoryRepository(directory).loadConversation("course", session);
        assertEquals(2, restored.size());
        assertEquals("user", restored.get(0).getRole());
        assertTrue(restored.get(0).getText().contains("\nUse the notes"));
        assertTrue(repository.loadConversation("course", UUID.randomUUID().toString()).isEmpty());
    }

    @Test
    public void rejectsAnotherCourseWithoutChangingTheConversation() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-memory-scope-");
        MemoryRepository repository = new MemoryRepository(directory);
        String session = UUID.randomUUID().toString();
        repository.saveConversation("first", session, Arrays.asList(new Message("user", "Show progress.")));
        Path file = directory.resolve("conversations").resolve(session + ".json");
        String before = Files.readString(file);
        try {
            repository.saveConversation("second", session, Arrays.asList(new Message("user", "Change scope.")));
            fail("A session must remain in its original course.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("another course"));
        }
        assertEquals(before, Files.readString(file));
    }

    @Test
    public void keepsACorruptConversationWhenLoadingFails() throws Exception {
        Path directory = Files.createTempDirectory("studypilot-memory-damage-");
        Path folder = directory.resolve("conversations");
        Files.createDirectories(folder);
        String session = UUID.randomUUID().toString();
        Path file = folder.resolve(session + ".json");
        Files.writeString(file, "{broken conversation");
        try {
            new MemoryRepository(directory).loadConversation("course", session);
            fail("Damaged memory must not be replaced.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("original file was not changed"));
        }
        assertEquals("{broken conversation", Files.readString(file));
    }

    @Test
    public void keepsInMemoryMessagesUnchangedWhenSavingFails() throws Exception {
        MemoryRepository failing = new MemoryRepository(Files.createTempDirectory("studypilot-memory-failure-")) {
            public void saveConversation(String courseId, String sessionId, List<Message> messages) {
                throw new IllegalStateException("Test memory save failure.");
            }
        };
        MemoryManager manager = new MemoryManager(failing);
        manager.loadSession("course", null);
        try {
            manager.addMessage(new Message("user", "Show progress."));
            fail("A failed save must be reported.");
        } catch (IllegalStateException e) {
            assertEquals("Test memory save failure.", e.getMessage());
        }
        assertTrue(manager.getHistory().isEmpty());
    }
}
