package com.in6222.litsync.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class AppLanguageManager {

    private static final String PREFS = "app_language";
    private static final String KEY_LANGUAGE_TAG = "language_tag";

    private AppLanguageManager() {
    }

    public static void applySavedLanguage(Context context) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(getSavedLanguageTag(context)));
    }

    public static String getSavedLanguageTag(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return preferences.getString(KEY_LANGUAGE_TAG, "");
    }

    public static void setLanguage(Context context, String languageTag) {
        String normalizedTag = languageTag == null ? "" : languageTag.trim();
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_LANGUAGE_TAG, normalizedTag).apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalizedTag));
    }
}
