package com.quizmaster.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class QuizCacheStore {

    private static final String PREFS_FILE = "quiz_cache_prefs";
    private static final String KEY_CACHE = "cache";

    public static class Entry {
        public final String uri;
        public final String displayName;
        public final String topic;
        public final List<QuizQuestion> questions;
        public final String textCacheKey;

        public Entry(String uri, String displayName, String topic, List<QuizQuestion> questions, String textCacheKey) {
            this.uri = uri;
            this.displayName = displayName;
            this.topic = topic;
            this.questions = questions;
            this.textCacheKey = textCacheKey;
        }
    }

    public static List<Entry> getAll(Context context) {
        List<Entry> entries = new ArrayList<>();
        String json = getPrefs(context).getString(KEY_CACHE, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                entries.add(parseEntry(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            // Corrupted cache; treat as empty.
        }
        return entries;
    }

    public static Entry find(Context context, String uri) {
        for (Entry entry : getAll(context)) {
            if (entry.uri.equals(uri)) {
                return entry;
            }
        }
        return null;
    }

    public static void save(Context context, String uri, String displayName, String topic,
                             List<QuizQuestion> questions, String textCacheKey) {
        List<Entry> entries = getAll(context);
        entries.removeIf(e -> e.uri.equals(uri));
        entries.add(0, new Entry(uri, displayName, topic, questions, textCacheKey));

        JSONArray array = new JSONArray();
        try {
            for (Entry e : entries) {
                array.put(toJson(e));
            }
        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize quiz cache", e);
        }

        getPrefs(context).edit().putString(KEY_CACHE, array.toString()).apply();
    }

    public static void delete(Context context, String uri) {
        List<Entry> entries = getAll(context);
        entries.removeIf(e -> e.uri.equals(uri));

        JSONArray array = new JSONArray();
        try {
            for (Entry e : entries) {
                array.put(toJson(e));
            }
        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize quiz cache", e);
        }

        getPrefs(context).edit().putString(KEY_CACHE, array.toString()).apply();
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
        json.put("uri", entry.uri);
        json.put("displayName", entry.displayName);
        json.put("topic", entry.topic);
        json.put("questions", questionsJson);
        json.put("textCacheKey", entry.textCacheKey);
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
        return new Entry(json.getString("uri"), json.getString("displayName"), json.getString("topic"), questions,
                json.optString("textCacheKey", null));
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }
}
