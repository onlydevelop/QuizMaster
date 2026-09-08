package com.quizmaster.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public class DocumentTextCache {

    private static final String PREFS_FILE = "document_text_cache_prefs";
    private static final String KEY_CACHE = "cache";

    public static String get(Context context, String uri) {
        return getCache(context).optString(uri, null);
    }

    public static void save(Context context, String uri, String text) {
        JSONObject cache = getCache(context);
        try {
            cache.put(uri, text);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to cache extracted document text", e);
        }
        getPrefs(context).edit().putString(KEY_CACHE, cache.toString()).apply();
    }

    public static void delete(Context context, String uri) {
        JSONObject cache = getCache(context);
        cache.remove(uri);
        getPrefs(context).edit().putString(KEY_CACHE, cache.toString()).apply();
    }

    private static JSONObject getCache(Context context) {
        String json = getPrefs(context).getString(KEY_CACHE, "{}");
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }
}
