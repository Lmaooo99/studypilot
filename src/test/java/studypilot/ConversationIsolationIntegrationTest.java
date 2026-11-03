package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationIsolationIntegrationTest {
    private AppConfig config;
    private MockLLMClient llm;
    private AgentFacade facade;
    private Course first;
    private Course other;

    @Before
    public void setUp() throws Exception {
        config = new AppConfig(Files.createTempDirectory("studypilot-conversation-isolation-"));
        llm = new MockLLMClient();
        facade = new AgentFacade(config, llm);
        first = facade.createCourse("Observer selected course");
        other = facade.createCourse("Separate Strategy course");
    }

    private AgentResult progress(Course course, String session, String request) {
        llm.reply("{\"steps\":[{\"stepId\":\"progress\",\"toolName\":\"progress\",\"arguments\":{}}]}");
        return facade.runAgent(course.getId(), session, request);
    }

    private Path conversation(String session) {
        return config.getDataDirectory().resolve("conversations").resolve(session + ".json");
    }

    @Test
    public void switchingCoursesAndReturningRestoresOnlyTheSelectedConversation() throws Exception {
        Course savedCourse = facade.getCourse(first.getId());
        Flashcard card = new Flashcard("Who receives notifications?", "Subscribers.", "Observer");
        savedCourse.setFlashcards(Arrays.asList(card));
        new CourseRepository(config.getDataDirectory()).saveCourse(savedCourse);
        facade.reviewCard(first.getId(), card.getId(), true);
        AgentResult firstResult = progress(first, null, "Only the Observer request marker.");
        AgentResult otherResult = progress(other, null, "Only the Strategy request marker.");
        String otherBefore = Files.readString(conversation(otherResult.getSessionId()));
        AgentResult resumed = progress(first, firstResult.getSessionId(), "Continue my selected conversation.");
        String prompt = llm.getPrompts().get(2);
        assertEquals(ResultStatus.OK, resumed.getStatus());
        assertTrue(prompt.contains("Only the Observer request marker."));
        assertTrue(prompt.contains("Observer selected course"));
        assertFalse(prompt.contains("Only the Strategy request marker."));
        assertFalse(prompt.contains("Separate Strategy course"));
        assertEquals("1", resumed.getResults().get("progress").getData().get("reviewsDone"));
        assertEquals("0", otherResult.getResults().get("progress").getData().get("reviewsDone"));
        assertEquals(otherBefore, Files.readString(conversation(otherResult.getSessionId())));
        MemoryRepository saved = new MemoryRepository(config.getDataDirectory());
        assertEquals(4, saved.loadConversation(first.getId(), firstResult.getSessionId()).size());
        assertEquals(2, saved.loadConversation(other.getId(), otherResult.getSessionId()).size());
    }

    @Test
    public void twoSessionsInOneCourseRemainSeparateAfterRestart() throws Exception {
        AgentResult left = progress(first, null, "Left session request marker.");
        AgentResult right = progress(first, null, "Right session request marker.");
        assertFalse(left.getSessionId().equals(right.getSessionId()));
        String rightBefore = Files.readString(conversation(right.getSessionId()));
        MockLLMClient reader = new MockLLMClient();
        reader.reply("{\"steps\":[],\"clarification\":\"Which activity should follow?\"}");
        AgentResult resumed = new AgentFacade(config, reader).runAgent(first.getId(), left.getSessionId(), "Continue this session.");
        assertTrue(resumed.needsClarification());
        assertTrue(reader.getPrompts().get(0).contains("Left session request marker."));
        assertFalse(reader.getPrompts().get(0).contains("Right session request marker."));
        assertEquals(rightBefore, Files.readString(conversation(right.getSessionId())));
        assertEquals(4, new MemoryRepository(config.getDataDirectory()).loadConversation(first.getId(), left.getSessionId()).size());
    }

    @Test
    public void aCrossCourseSessionFailsBeforePlanningAndDoesNotChangeItsOwner() throws Exception {
        AgentResult owned = progress(first, null, "Keep the owner request marker.");
        Path file = conversation(owned.getSessionId());
        String before = Files.readString(file);
        try {
            facade.runAgent(other.getId(), owned.getSessionId(), "Rejected cross-course request marker.");
            fail("A saved session must retain its course.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("another course"));
        }
        assertEquals(1, llm.getPrompts().size());
        assertEquals(before, Files.readString(file));
        progress(first, owned.getSessionId(), "Resume the owner session.");
        assertFalse(llm.getPrompts().get(1).contains("Rejected cross-course request marker."));
        assertFalse(Files.readString(file).contains("Rejected cross-course request marker."));
        assertEquals(first.getId(), new JSONObject(Files.readString(file)).getString("courseId"));
    }

    @Test
    public void invalidSessionPathsAreRejectedBeforePlanningOrWriting() throws Exception {
        AgentResult valid = progress(first, null, "An existing valid conversation.");
        Path file = conversation(valid.getSessionId());
        String before = Files.readString(file);
        String[] invalid = {"../" + valid.getSessionId(), "not-a-session-id"};
        for (String session : invalid) {
            try {
                facade.runAgent(first.getId(), session, "Do not write outside conversations.");
                fail("Invalid session IDs must not reach the planner.");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Invalid saved object ID"));
            }
        }
        assertEquals(1, llm.getPrompts().size());
        assertEquals(before, Files.readString(file));
        assertFalse(Files.exists(config.getDataDirectory().resolve(valid.getSessionId() + ".json")));
    }

    @Test
    public void invalidSavedMessagesStayIntactAndNeverReachThePlanner() throws Exception {
        AgentResult result = progress(first, null, "A valid conversation to damage.");
        Path file = conversation(result.getSessionId());
        String original = Files.readString(file);
        String[] fields = {"role", "text", "text"};
        Object[] values = {"system", "", 7};
        for (int i = 0; i < fields.length; i++) {
            JSONObject data = new JSONObject(original);
            data.getJSONArray("messages").getJSONObject(0).put(fields[i], values[i]);
            String damaged = data.toString();
            Files.writeString(file, damaged);
            MockLLMClient unused = new MockLLMClient();
            try {
                new AgentFacade(config, unused).runAgent(first.getId(), result.getSessionId(), "Do not repair damaged messages.");
                fail("Invalid stored messages must be reported.");
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("original file was not changed"));
            }
            assertEquals(0, unused.getPrompts().size());
            assertEquals(damaged, Files.readString(file));
        }
    }

    @Test
    public void corruptConversationJsonIsPreservedBeforeAnyModelCall() throws Exception {
        AgentResult result = progress(first, null, "A valid session before corruption.");
        Path file = conversation(result.getSessionId());
        Files.writeString(file, "{broken conversation");
        MockLLMClient unused = new MockLLMClient();
        try {
            new AgentFacade(config, unused).runAgent(first.getId(), result.getSessionId(), "Do not discard the saved bytes.");
            fail("Unreadable conversation JSON must not be overwritten.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("original file was not changed"));
        }
        assertEquals("{broken conversation", Files.readString(file));
        assertEquals(0, unused.getPrompts().size());
    }

    @Test
    public void plannerUsesTenRecentMessagesWhileTheSavedHistoryRetainsEveryTurn() {
        String session = null;
        for (int i = 0; i < 6; i++) {
            session = progress(first, session, "Unique turn marker " + i + ".").getSessionId();
        }
        MockLLMClient reader = new MockLLMClient();
        reader.reply("{\"steps\":[],\"clarification\":\"Which activity next?\"}");
        AgentResult followup = new AgentFacade(config, reader).runAgent(first.getId(), session, "Continue after restarting.");
        String prompt = reader.getPrompts().get(0);
        assertFalse(prompt.contains("Unique turn marker 0."));
        for (int i = 1; i < 6; i++) {
            assertTrue(prompt.contains("Unique turn marker " + i + "."));
        }
        List<Message> saved = new MemoryRepository(config.getDataDirectory()).loadConversation(first.getId(), session);
        assertEquals(14, saved.size());
        assertEquals("Unique turn marker 0.", saved.get(0).getText());
        assertEquals(followup.getFinalAnswer(), saved.get(13).getText());
    }
}
