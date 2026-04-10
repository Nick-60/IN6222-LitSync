package com.in6222.litsync.ai;

import com.in6222.litsync.BuildConfig;

import java.util.Locale;

public class AiConfig {

    public enum ProviderType {
        BIGMODEL,
        GROQ,
        OPENAI_COMPATIBLE;

        static ProviderType from(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return BIGMODEL;
            }
            String normalized = rawValue.trim().toUpperCase(Locale.US);
            for (ProviderType providerType : values()) {
                if (providerType.name().equals(normalized)) {
                    return providerType;
                }
            }
            return BIGMODEL;
        }
    }

    private final ProviderType providerType;
    private final String baseUrl;
    private final String model;
    private final String apiKey;

    public AiConfig(ProviderType providerType, String baseUrl, String model, String apiKey) {
        this.providerType = providerType;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.model = model == null ? "" : model.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public static AiConfig fromBuildConfig() {
        return new AiConfig(
                ProviderType.from(BuildConfig.AI_PROVIDER),
                BuildConfig.AI_BASE_URL,
                BuildConfig.AI_MODEL,
                BuildConfig.AI_API_KEY
        );
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getRawBaseUrl() {
        return baseUrl;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !model.isEmpty() && !getChatCompletionsUrl().isEmpty();
    }

    public String getChatCompletionsUrl() {
        if (baseUrl.isEmpty()) {
            return "";
        }
        if (baseUrl.endsWith("/chat/completions") || baseUrl.endsWith("chat/completions")) {
            return baseUrl;
        }
        return baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
    }
}
