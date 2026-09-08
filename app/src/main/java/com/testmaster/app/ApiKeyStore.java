package com.testmaster.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ApiKeyStore {

    private static final String PREFS_FILE = "secure_api_key_prefs";
    private static final String KEY_ANTHROPIC_API_KEY = "anthropic_api_key";

    public static void save(Context context, String apiKey) {
        getPrefs(context).edit().putString(KEY_ANTHROPIC_API_KEY, apiKey).apply();
    }

    public static String get(Context context) {
        return getPrefs(context).getString(KEY_ANTHROPIC_API_KEY, null);
    }

    private static SharedPreferences getPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Unable to access encrypted API key storage", e);
        }
    }
}
