package studypilot;

public class Message {
    private final String role;
    private final String text;

    public Message(String role, String text) {
        if ((!"user".equals(role) && !"assistant".equals(role)) || text == null || text.isBlank()) {
            throw new IllegalArgumentException("A message needs a user/assistant role and nonempty text.");
        }
        this.role = role;
        this.text = text;
    }

    public String getRole() {
        return role;
    }

    public String getText() {
        return text;
    }
}
