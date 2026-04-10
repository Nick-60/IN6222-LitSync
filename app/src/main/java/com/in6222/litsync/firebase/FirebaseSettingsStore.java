package com.in6222.litsync.firebase;

import android.content.Context;
import android.content.SharedPreferences;

public class FirebaseSettingsStore {

    private static final String PREFS_NAME = "firebase_sync_settings";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences preferences;

    public FirebaseSettingsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String loadEmail() {
        return preferences.getString(KEY_EMAIL, "");
    }

    public void saveEmail(String email) {
        preferences.edit()
                .putString(KEY_EMAIL, email == null ? "" : email.trim())
                .apply();
    }
}
