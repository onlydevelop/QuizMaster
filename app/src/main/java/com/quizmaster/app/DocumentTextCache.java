package com.quizmaster.app;

import android.content.Context;

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
        JsonBlobStore.write(context, PREFS_FILE, KEY_CACHE, cache);
    }

    public static void delete(Context context, String uri) {
        JSONObject cache = getCache(context);
        cache.remove(uri);
        JsonBlobStore.write(context, PREFS_FILE, KEY_CACHE, cache);
    }

    private static JSONObject getCache(Context context) {
        return JsonBlobStore.readObject(context, PREFS_FILE, KEY_CACHE);
    }
}
