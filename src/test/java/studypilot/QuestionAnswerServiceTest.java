package studypilot;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuestionAnswerServiceTest {
    private MockLLMClient llm;
    private Course course;
    private Chunk source;
    private QuestionAnswerService service;

    @Before
    public void setUp() {
        llm = new MockLLMClient();
        course = new Course("Design");
        source = new Chunk("Observers receive subject updates.", "lecture.pdf", 3, 0, 0);
        List<Chunk> chunks = new ArrayList<>();
        chunks.add(source);
        course.addMaterial(new Material("lecture.pdf", "test-hash", chunks));
        service = new QuestionAnswerService(new TfIdfRetrieval(), llm, new PromptTemplates(), new ResponseParser(), new CitationValidator());
    }

    private String answer(String... ids) {
        JSONArray array = new JSONArray();
        for (String id : ids) {
            array.put(id);
        }
        JSONObject data = new JSONObject();
        data.put("answer", "Observers receive updates.");
        data.put("citedChunkIds", array);
        return data.toString();
    }

    @Test
    public void removesUnknownCitationsAndKeepsTheActualPdfPage() {
        llm.reply(answer("invented-id", source.getId(), source.getId()));
        Answer result = service.answer(course, "Observers");
        assertTrue(result.hasValidCitation());
        assertEquals(1, result.getCitations().size());
        assertEquals(3, result.getCitations().get(0).getPage());
        assertTrue(result.toString().contains("lecture.pdf, page 3"));
    }

    @Test
    public void avoidsCallingTheModelWhenRetrievalFindsNothing() {
        Answer result = service.answer(course, "photosynthesis");
        assertFalse(result.hasValidCitation());
        assertEquals(0, llm.getPrompts().size());
    }

    @Test
    public void retriesAnUnsupportedCitationAndAcceptsACorrectedReply() {
        llm.reply(answer("unknown"));
        llm.reply(answer(source.getId()));
        assertTrue(service.answer(course, "Observers").hasValidCitation());
        assertEquals(2, llm.getPrompts().size());
    }

    @Test
    public void doesNotPresentTwoUnsupportedRepliesAsAValidatedAnswer() {
        llm.reply(answer("unknown"));
        llm.reply(answer("unknown"));
        try {
            service.answer(course, "Observers");
            fail("A cited answer must have a retrieved citation.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("valid citations"));
        }
    }

    @Test
    public void canAskForClarificationInsteadOfInventingAnAnswer() {
        llm.reply("{\"answer\":\"Which observer topic do you mean?\",\"citedChunkIds\":[],\"needsClarification\":true}");
        Answer result = service.answer(course, "Observers");
        assertFalse(result.hasValidCitation());
        assertTrue(result.getText().startsWith("Clarification needed:"));
    }
}
