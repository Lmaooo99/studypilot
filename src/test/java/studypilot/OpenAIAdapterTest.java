package studypilot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenAIAdapterTest {
    private HttpServer server;
    private OpenAIAdapter adapter;
    private Path directory;
    private String requestBody;
    private String reply;
    private int status;
    private String authorization;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("studypilot-http-test-");
        status = 200;
        reply = "{\"choices\":[{\"message\":{\"content\":\"A useful summary.\"}}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            }
        });
        server.start();
        Files.writeString(directory.resolve(".env"), "LLM_BASE_URL=http://127.0.0.1:"
                + server.getAddress().getPort() + "/v1\nLLM_API_KEY=test-only-api-key\n");
        AppConfig config = new AppConfig(directory);
        adapter = new OpenAIAdapter(config.getModel(), new OpenAIApiClient(config));
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void translatesARequestAndReadsTheResponse() throws Exception {
        LLMResponse response = adapter.generate("Summarize this source.");
        assertTrue(response.isSuccess());
        assertEquals("A useful summary.", response.getText());
        JSONObject body = new JSONObject(requestBody);
        assertEquals("gpt-6.1-sol", body.getString("model"));
        assertEquals("Summarize this source.", body.getJSONArray("messages").getJSONObject(1).getString("content"));
        assertTrue(authorization.equals("Bearer test-only-api-key"));
        String usage = Files.readString(directory.resolve("data/llm-usage.jsonl"));
        assertEquals(15, new JSONObject(usage).getInt("total_tokens"));
        assertFalse(usage.contains("test-only-api-key"));
        assertFalse(usage.contains("Summarize this source"));
    }

    @Test
    public void hidesTheErrorBodyWhenAuthenticationFails() {
        status = 401;
        reply = "Private diagnostic: test-only-api-key";
        LLMResponse response = adapter.generate("A test request.");
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("authentication"));
        assertFalse(response.getErrorMessage().contains("test-only-api-key"));
    }

    @Test
    public void rejectsMalformedServiceResponses() {
        reply = "{\"choices\":[]}";
        LLMResponse response = adapter.generate("A test request.");
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("invalid response"));
    }

    @Test
    public void reportsRateLimitsWithoutReturningProviderDiagnostics() {
        status = 429;
        reply = "Provider internal details";
        LLMResponse response = adapter.generate("A test request.");
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("rate-limited"));
        assertFalse(response.getErrorMessage().contains("internal"));
    }

    @Test
    public void canOmitTheOptionalReasoningParameter() {
        AppConfig config = new AppConfig(directory);
        adapter = new OpenAIAdapter(config.getModel(), new OpenAIApiClient(config), "");
        assertTrue(adapter.generate("A test request.").isSuccess());
        assertFalse(new JSONObject(requestBody).has("reasoning_effort"));
    }

    @Test
    public void rejectsOutputThatReachedTheCompletionLimit() {
        reply = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"unfinished\"}}]}";
        LLMResponse response = adapter.generate("A test request.");
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("output limit"));
    }
}
