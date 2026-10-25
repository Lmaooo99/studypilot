package studypilot;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppConfig {
    private final Properties values;
    private final Path directory;

    public AppConfig(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        values = new Properties();
        Path file = this.directory.resolve(".env");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                values.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("The local configuration could not be read.");
            }
        }
    }

    private String get(String name, String defaultValue) {
        return values.getProperty(name, defaultValue).trim();
    }

    public Path getDataDirectory() {
        return directory.resolve(get("DATA_DIR", "data")).normalize();
    }

    public String getModel() {
        return get("LLM_MODEL", "gpt-6.1-sol");
    }

    public String getApiKey() {
        return get("LLM_API_KEY", "");
    }

    public String getReasoningEffort() {
        return get("LLM_REASONING_EFFORT", "low");
    }

    public String getBaseUrl() {
        return get("LLM_BASE_URL", "").replaceAll("/+$", "");
    }

    public String getRetrievalMode() {
        String mode = get("RETRIEVAL_MODE", "tfidf");
        if (!mode.equals("keyword") && !mode.equals("tfidf")) {
            throw new IllegalArgumentException("RETRIEVAL_MODE must be keyword or tfidf.");
        }
        return mode;
    }

    public void validateLlmSettings() {
        validateApiKey();
        validateBaseUrl();
        if (getModel().isEmpty()) {
            throw new IllegalArgumentException("Set LLM_MODEL in your local .env file.");
        }
    }

    private void validateApiKey() {
        String apiKey = getApiKey();
        if (apiKey.isEmpty() || apiKey.equals("YOUR_LOCAL_API_KEY_HERE")) {
            throw new IllegalArgumentException("Set LLM_API_KEY in your local .env file.");
        }
        for (int i = 0; i < apiKey.length(); i++) {
            char character = apiKey.charAt(i);
            if (character < 33 || character > 126) {
                throw new IllegalArgumentException("LLM_API_KEY must contain printable ASCII characters without spaces.");
            }
        }
    }

    private void validateBaseUrl() {
        URI uri;
        try {
            uri = URI.create(getBaseUrl());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("LLM_BASE_URL must be a valid API base URL.");
        }
        String host = uri.getHost();
        boolean isLocal = "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        boolean isHttps = "https".equals(uri.getScheme());
        boolean isLocalHttp = isLocal && "http".equals(uri.getScheme());
        if (host == null || (!isHttps && !isLocalHttp) || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Use an HTTPS API base URL without credentials or a query.");
        }
    }
}
