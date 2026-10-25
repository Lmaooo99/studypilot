package studypilot;

import java.util.ArrayList;
import java.util.List;

// Test double for repeatable service tests. Production code never constructs it.
public class MockLLMClient implements LLMClient {
    private final List<LLMResponse> scriptedReplies = new ArrayList<>();
    private final List<String> prompts = new ArrayList<>();

    public void reply(String text) {
        scriptedReplies.add(new LLMResponse(text, true, ""));
    }

    public void failNext() {
        scriptedReplies.add(new LLMResponse("", false, "Test service failure."));
    }

    public List<String> getPrompts() {
        return new ArrayList<>(prompts);
    }

    public LLMResponse generate(String prompt) {
        prompts.add(prompt);
        if (scriptedReplies.isEmpty()) {
            return new LLMResponse("", false, "No scripted test reply.");
        }
        return scriptedReplies.remove(0);
    }
}
