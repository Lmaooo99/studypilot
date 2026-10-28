package studypilot;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppConfigTest {
    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("studypilot-config-test-");
    }

    @Test
    public void allowsLocalFeaturesWithoutCredentials() {
        AppConfig config = new AppConfig(directory);
        assertEquals(directory.resolve("data"), config.getDataDirectory());
        assertEquals("gpt-6.1-sol", config.getModel());
        assertEquals(5, config.getWeakTopicMinResults());
    }

    @Test
    public void readsTheWeakTopicMinimum() throws Exception {
        Files.writeString(directory.resolve(".env"), "WEAK_TOPIC_MIN_RESULTS=3\n");
        assertEquals(3, new AppConfig(directory).getWeakTopicMinResults());
    }

    @Test
    public void rejectsAnInvalidWeakTopicMinimum() throws Exception {
        String[] values = {"0", "-1", "text", "1.5", "999999999999999999999"};
        for (String value : values) {
            Files.writeString(directory.resolve(".env"), "WEAK_TOPIC_MIN_RESULTS=" + value + "\n");
            try {
                new AppConfig(directory).getWeakTopicMinResults();
                fail("The weak-topic minimum must be a positive integer.");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("WEAK_TOPIC_MIN_RESULTS"));
            }
        }
    }

    @Test
    public void rejectsMissingCredentialsBeforeAnLlmCall() {
        try {
            new AppConfig(directory).validateLlmSettings();
            fail("Missing credentials must be rejected.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("LLM_API_KEY"));
        }
    }

    @Test
    public void readsLocalSettingsAndRemovesTrailingSlashes() throws Exception {
        Files.writeString(directory.resolve(".env"),
                "LLM_BASE_URL=https://example.invalid/v1/\nLLM_API_KEY=test-only-key\nDATA_DIR=study-data\n");
        AppConfig config = new AppConfig(directory);
        config.validateLlmSettings();
        assertEquals("https://example.invalid/v1", config.getBaseUrl());
        assertEquals(directory.resolve("study-data"), config.getDataDirectory());
    }

    @Test
    public void rejectsCredentialBearingUrlsWithoutEchoingSecrets() throws Exception {
        Files.writeString(directory.resolve(".env"),
                "LLM_BASE_URL=https://hidden-password@example.invalid/v1\nLLM_API_KEY=test-only-key\n");
        try {
            new AppConfig(directory).validateLlmSettings();
            fail("A credential-bearing URL must be rejected.");
        } catch (IllegalArgumentException e) {
            assertFalse(e.getMessage().contains("hidden-password"));
        }
    }

    @Test
    public void rejectsAnUnknownRetrievalAlgorithm() throws Exception {
        Files.writeString(directory.resolve(".env"), "RETRIEVAL_MODE=unknown\n");
        try {
            new AppConfig(directory).getRetrievalMode();
            fail("An unknown retrieval mode must be rejected.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("keyword or tfidf"));
        }
    }

    @Test
    public void rejectsMalformedKeyCharactersWithoutPrintingTheValue() throws Exception {
        Files.writeString(directory.resolve(".env"), "LLM_BASE_URL=https://example.invalid/v1\nLLM_API_KEY=test-only-key with spaces\n");
        try {
            new AppConfig(directory).validateLlmSettings();
            fail("Malformed header values must be rejected safely.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("printable ASCII"));
            assertFalse(e.getMessage().contains("test-only-key"));
        }
    }
}
