package studypilot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import org.json.JSONObject;

public class OpenAIApiClient {
    private final AppConfig config;
    private final HttpClient client;

    public OpenAIApiClient(AppConfig config) {
        this.config = config;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String sendRequest(String jsonBody) {
        config.validateLlmSettings();
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new IllegalStateException("LLM authentication failed. Check your local API configuration.");
            }
            if (status == 429) {
                throw new IllegalStateException("The LLM service is rate-limited. Try again later.");
            }
            if (status != 200) {
                throw new IllegalStateException("The LLM service returned HTTP " + status + ".");
            }
            return response.body();
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException("The LLM request timed out. Try again later.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The LLM request was interrupted.");
        } catch (IOException e) {
            throw new IllegalStateException("The LLM service could not be reached.");
        }
    }

    public void recordUsage(JSONObject response) {
        JSONObject usage = response.optJSONObject("usage");
        if (usage == null) {
            return;
        }
        JSONObject entry = new JSONObject();
        entry.put("time", Instant.now().toString());
        entry.put("model", config.getModel());
        for (String name : new String[] {"prompt_tokens", "completion_tokens", "total_tokens"}) {
            Object value = usage.opt(name);
            if (value instanceof Number) {
                entry.put(name, value);
            }
        }
        try {
            Files.createDirectories(config.getDataDirectory());
            Files.writeString(config.getDataDirectory().resolve("llm-usage.jsonl"), entry + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("LLM usage statistics could not be saved.");
        }
    }
}
