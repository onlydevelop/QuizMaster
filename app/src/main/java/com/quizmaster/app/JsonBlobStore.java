package com.quizmaster.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Shared "JSON blob in a SharedPreferences string" persistence used by the
 * app's small on-device caches (QuizCacheStore, DocumentTextCache).
 */
final class JsonBlobStore {

    private JsonBlobStore() {
    }

    static JSONObject readObject(Context context, String prefsFile, String key) {
        String json = getPrefs(context, prefsFile).getString(key, "{}");
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static JSONArray readArray(Context context, String prefsFile, String key) {
        String json = getPrefs(context, prefsFile).getString(key, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void write(Context context, String prefsFile, String key, Object jsonValue) {
        getPrefs(context, prefsFile).edit().putString(key, jsonValue.toString()).apply();
    }

    private static SharedPreferences getPrefs(Context context, String prefsFile) {
        return context.getSharedPreferences(prefsFile, Context.MODE_PRIVATE);
    }
}
