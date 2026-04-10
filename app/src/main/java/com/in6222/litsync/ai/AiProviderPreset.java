package com.in6222.litsync.ai;

import java.util.ArrayList;
import java.util.List;

public class AiProviderPreset {

    public static final String ID_GLM = "glm";
    public static final String ID_GROQ = "groq";
    public static final String ID_DEEPSEEK = "deepseek";
    public static final String ID_SILICON_FLOW = "silicon_flow";
    public static final String ID_OPENAI = "openai";
    public static final String ID_CUSTOM = "custom";

    private final String id;
    private final String displayName;
    private final AiConfig.ProviderType providerType;
    private final String baseUrl;
    private final String model;

    public AiProviderPreset(String id, String displayName, AiConfig.ProviderType providerType, String baseUrl, String model) {
        this.id = id;
        this.displayName = displayName;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public static List<AiProviderPreset> defaults() {
        List<AiProviderPreset> presets = new ArrayList<>();
        presets.add(new AiProviderPreset(
                ID_GLM,
                "GLM-4.7-Flash",
                AiConfig.ProviderType.BIGMODEL,
                "https://open.bigmodel.cn/api/paas/v4/",
                "glm-4.7-flash"
        ));
        presets.add(new AiProviderPreset(
                ID_GROQ,
                "Groq Llama",
                AiConfig.ProviderType.GROQ,
                "https://api.groq.com/openai/v1/",
                "llama-3.3-70b-versatile"
        ));
        presets.add(new AiProviderPreset(
                ID_DEEPSEEK,
                "DeepSeek Chat",
                AiConfig.ProviderType.OPENAI_COMPATIBLE,
                "https://api.deepseek.com/v1/",
                "deepseek-chat"
        ));
        presets.add(new AiProviderPreset(
                ID_SILICON_FLOW,
                "SiliconFlow Qwen",
                AiConfig.ProviderType.OPENAI_COMPATIBLE,
                "https://api.siliconflow.cn/v1/",
                "Qwen/Qwen2.5-7B-Instruct"
        ));
        presets.add(new AiProviderPreset(
                ID_OPENAI,
                "OpenAI GPT-4.1 mini",
                AiConfig.ProviderType.OPENAI_COMPATIBLE,
                "https://api.openai.com/v1/",
                "gpt-4.1-mini"
        ));
        presets.add(new AiProviderPreset(
                ID_CUSTOM,
                "Custom Compatible API",
                AiConfig.ProviderType.OPENAI_COMPATIBLE,
                "",
                ""
        ));
        return presets;
    }

    public static AiProviderPreset findById(String id) {
        for (AiProviderPreset preset : defaults()) {
            if (preset.id.equals(id)) {
                return preset;
            }
        }
        return defaults().get(0);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AiConfig.ProviderType getProviderType() {
        return providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }
}
