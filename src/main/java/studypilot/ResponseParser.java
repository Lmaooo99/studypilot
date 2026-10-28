package studypilot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
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

    public List<RawQuestion> parseRawQuestions(String text) {
        JSONArray items = parseObject(text).getJSONArray("questions");
        List<RawQuestion> questions = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                QuestionType type = QuestionType.parse(item.getString("type"));
                List<String> options = new ArrayList<>();
                if (type == QuestionType.MULTIPLE_CHOICE) {
                    options = parseStringList(item.getJSONArray("options"));
                }
                String rubric = "";
                if (type == QuestionType.SHORT_ANSWER) {
                    rubric = item.getString("rubric");
                }
                questions.add(new RawQuestion(type, item.getString("text"), options,
                        item.getString("correctAnswer"), rubric, item.getString("conceptName")));
            } catch (JSONException | IllegalArgumentException e) {
                // One malformed draft must not discard the other questions.
            }
        }
        return questions;
    }

    public Map<String, String> parseAnswers(String text) {
        JSONObject saved = parseObject(text).getJSONObject("answers");
        Map<String, String> answers = new HashMap<>();
        for (String id : saved.keySet()) {
            answers.put(id, saved.getString(id));
        }
        return answers;
    }

    public QuestionResult parseGrading(String text, Question question) {
        JSONObject data = parseObject(text);
        Object value = data.get("pointsEarned");
        if (!(value instanceof Integer)) {
            throw new JSONException("Grading points must be a JSON integer");
        }
        int points = (Integer) value;
        if (points < 0 || points > question.getPoints()) {
            throw new JSONException("Grading points must be within the question's range");
        }
        String explanation = data.getString("explanation");
        if (explanation.isBlank()) {
            throw new JSONException("Missing grading explanation");
        }
        return new QuestionResult(question, GradingStatus.GRADED, points, explanation.trim());
    }

    public List<StudyTask> parseStudyTasks(String text) {
        JSONArray items = parseObject(text).getJSONArray("tasks");
        List<StudyTask> tasks = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (!(item.get("minutes") instanceof Integer)) {
                throw new JSONException("Task minutes must be a JSON integer");
            }
            tasks.add(new StudyTask(LocalDate.parse(item.getString("date")),
                    TaskType.valueOf(item.getString("type")), item.getString("conceptName"),
                    item.getString("description"), item.getInt("minutes")));
        }
        return tasks;
    }
}
