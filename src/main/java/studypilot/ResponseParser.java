package studypilot;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ResponseParser {
    private JSONObject parseObject(String text) {
        String clean = text.trim();
        if (clean.startsWith("```")) {
            int newline = clean.indexOf('\n');
            if (newline < 0 || !clean.endsWith("```")) {
                throw new JSONException("Incomplete JSON fence");
            }
            clean = clean.substring(newline + 1, clean.length() - 3).trim();
        }
        return new JSONObject(clean);
    }

    public List<Concept> parseConcepts(String text) {
        JSONObject data = parseObject(text);
        JSONArray items = data.getJSONArray("concepts");
        List<Concept> concepts = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String name = item.getString("name");
            String definition = item.getString("definition");
            if (name.isBlank() || definition.isBlank()) {
                throw new JSONException("Empty concept fields");
            }
            String parent = "";
            if (item.has("parentName")) {
                parent = item.getString("parentName");
            }
            List<String> sources = parseStringList(item.getJSONArray("sourceChunkIds"));
            if (sources.isEmpty()) {
                throw new JSONException("Missing concept sources");
            }
            concepts.add(new Concept(name, definition, parent, sources));
        }
        return concepts;
    }

    private List<String> parseStringList(JSONArray array) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.getString(i);
            if (value.isBlank()) {
                throw new JSONException("Empty JSON string");
            }
            values.add(value);
        }
        return values;
    }

    public CitedAnswer parseCitedAnswer(String text) {
        JSONObject data = parseObject(text);
        String answer = data.getString("answer");
        if (answer.isBlank()) {
            throw new JSONException("Empty answer");
        }
        if (data.has("needsClarification") && !(data.get("needsClarification") instanceof Boolean)) {
            throw new JSONException("Clarification flag must be a boolean");
        }
        List<String> citedIds = parseStringList(data.getJSONArray("citedChunkIds"));
        boolean needsClarification = data.optBoolean("needsClarification", false);
        return new CitedAnswer(answer, citedIds, needsClarification);
    }

    public List<Flashcard> parseFlashcards(String text) {
        JSONObject data = parseObject(text);
        JSONArray items = data.getJSONArray("cards");
        List<Flashcard> cards = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String front = item.getString("front");
            String back = item.getString("back");
            if (front.isBlank() || back.isBlank()) {
                throw new JSONException("Empty flashcard fields");
            }
            String conceptName = "";
            if (item.has("conceptName")) {
                conceptName = item.getString("conceptName");
            }
            cards.add(new Flashcard(front, back, conceptName));
        }
        return cards;
    }
}
