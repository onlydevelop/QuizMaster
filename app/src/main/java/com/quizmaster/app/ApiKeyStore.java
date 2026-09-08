package com.quizmaster.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ApiKeyStore {

    private static final String PREFS_FILE = "secure_api_key_prefs";
    private static final String KEY_ANTHROPIC_API_KEY = "anthropic_api_key";

    // Building the MasterKey and EncryptedSharedPreferences involves real
    // keystore/crypto work; cache the instance for the process lifetime
    // instead of redoing it on every get()/save() call.
    private static volatile SharedPreferences cachedPrefs;

    public static void save(Context context, String apiKey) {
        getPrefs(context).edit().putString(KEY_ANTHROPIC_API_KEY, apiKey).apply();
    }

    public static String get(Context context) {
        return getPrefs(context).getString(KEY_ANTHROPIC_API_KEY, null);
    }

    private static SharedPreferences getPrefs(Context context) {
        SharedPreferences prefs = cachedPrefs;
        if (prefs != null) {
            return prefs;
        }

        synchronized (ApiKeyStore.class) {
            if (cachedPrefs == null) {
                cachedPrefs = createPrefs(context.getApplicationContext());
            }
            return cachedPrefs;
        }
    }

    private static SharedPreferences createPrefs(Context appContext) {
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Unable to access encrypted API key storage", e);
        }
    }
}
