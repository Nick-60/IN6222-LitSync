package com.in6222.litsync.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

public class AiSettingsStore {

    private static final String PREFS_NAME = "ai_settings";
    private static final String KEY_PRESET_ID = "preset_id";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_MODEL = "model";
    private static final String KEY_API_KEY = "api_key";

    private final SharedPreferences preferences;

    public AiSettingsStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public AiConfig load() {
        AiConfig buildConfigValue = AiConfig.fromBuildConfig();
        String providerRaw = preferences.getString(KEY_PROVIDER, buildConfigValue.getProviderType().name());
        String baseUrl = preferences.getString(KEY_BASE_URL, buildConfigValue.getRawBaseUrl());
        String model = preferences.getString(KEY_MODEL, buildConfigValue.getModel());
        String apiKey = preferences.getString(KEY_API_KEY, buildConfigValue.getApiKey());
        return new AiConfig(AiConfig.ProviderType.from(providerRaw), baseUrl, model, apiKey);
    }

    public void save(String presetId, AiConfig config) {
        preferences.edit()
                .putString(KEY_PRESET_ID, presetId)
                .putString(KEY_PROVIDER, config.getProviderType().name())
                .putString(KEY_BASE_URL, config.getRawBaseUrl())
                .putString(KEY_MODEL, config.getModel())
                .putString(KEY_API_KEY, config.getApiKey())
                .apply();
    }

    public String getPresetId() {
        return preferences.getString(KEY_PRESET_ID, AiProviderPreset.ID_GLM);
    }
}
