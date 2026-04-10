package com.in6222.litsync.recommendation;

import android.content.Context;
import android.content.SharedPreferences;

public class RecommendationSettingsStore {

    private static final String PREFS_NAME = "recommendation_settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HOUR = "hour";
    private static final String KEY_MINUTE = "minute";

    private final SharedPreferences preferences;

    public RecommendationSettingsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    public int getHour() {
        return preferences.getInt(KEY_HOUR, 8);
    }

    public int getMinute() {
        return preferences.getInt(KEY_MINUTE, 0);
    }

    public void save(boolean enabled, int hour, int minute) {
        preferences.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();
    }
}
