package com.quizmaster.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizCacheStore {

    private static final String PREFS_FILE = "quiz_cache_prefs";
    private static final String KEY_CACHE = "cache";

    public static class Entry {
        public final String contentKey;
        public final String uri;
        public final String displayName;
        public final String topic;
        public final List<QuizQuestion> questions;

        public Entry(String contentKey, String uri, String displayName, String topic, List<QuizQuestion> questions) {
            this.contentKey = contentKey;
            this.uri = uri;
            this.displayName = displayName;
            this.topic = topic;
            this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
        }
    }

    public static List<Entry> getAll(Context context) {
        List<Entry> entries = new ArrayList<>();
        JSONArray array = JsonBlobStore.readArray(context, PREFS_FILE, KEY_CACHE);
        for (int i = 0; i < array.length(); i++) {
            try {
                entries.add(parseEntry(array.getJSONObject(i)));
            } catch (JSONException ignored) {
                // Corrupted entry; skip it.
            }
        }
        return entries;
    }

    /**
     * Looks up a cached question pool by the file's content identity
     * (see MainActivity.computeContentHash), not by picker URI - the
     * same physical file can come back from the document picker under
     * a different URI depending on how it was navigated to.
     */
    public static Entry find(Context context, String contentKey) {
        for (Entry entry : getAll(context)) {
            if (entry.contentKey.equals(contentKey)) {
                return entry;
            }
        }
        return null;
    }

    public static void save(Context context, PickedFile pickedFile, String topic, List<QuizQuestion> questions) {
        List<Entry> entries = getAll(context);
        entries.removeIf(e -> e.contentKey.equals(pickedFile.contentKey));
        entries.add(0, new Entry(pickedFile.contentKey, pickedFile.uri, pickedFile.displayName, topic, questions));
        persist(context, entries);
    }

    public static void delete(Context context, String contentKey) {
        List<Entry> entries = getAll(context);
        entries.removeIf(e -> e.contentKey.equals(contentKey));
        persist(context, entries);
    }

    private static void persist(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry e : entries) {
                array.put(toJson(e));
            }
        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize quiz cache", e);
        }
        JsonBlobStore.write(context, PREFS_FILE, KEY_CACHE, array);
    }

    private static JSONObject toJson(Entry entry) throws JSONException {
        JSONArray questionsJson = new JSONArray();
        for (QuizQuestion q : entry.questions) {
            JSONObject qJson = new JSONObject();
            qJson.put("question", q.question);
            qJson.put("choices", new JSONArray(q.choices));
            qJson.put("correctIndex", q.correctIndex);
            questionsJson.put(qJson);
        }

        JSONObject json = new JSONObject();
        json.put("contentKey", entry.contentKey);
        json.put("uri", entry.uri);
        json.put("displayName", entry.displayName);
        json.put("topic", entry.topic);
        json.put("questions", questionsJson);
        return json;
    }

    private static Entry parseEntry(JSONObject json) throws JSONException {
        JSONArray questionsJson = json.getJSONArray("questions");
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < questionsJson.length(); i++) {
            JSONObject qJson = questionsJson.getJSONObject(i);
            JSONArray choicesJson = qJson.getJSONArray("choices");
            List<String> choices = new ArrayList<>();
            for (int j = 0; j < choicesJson.length(); j++) {
                choices.add(choicesJson.getString(j));
            }
            questions.add(new QuizQuestion(qJson.getString("question"), choices, qJson.getInt("correctIndex")));
        }
        return new Entry(json.getString("contentKey"), json.getString("uri"), json.getString("displayName"),
                json.getString("topic"), questions);
    }
}
