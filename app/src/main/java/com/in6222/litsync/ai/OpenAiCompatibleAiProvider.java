package com.in6222.litsync.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OpenAiCompatibleAiProvider implements AiProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AiConfig config;
    private final OkHttpClient client;

    public OpenAiCompatibleAiProvider(AiConfig config) {
        this.config = config;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String complete(List<AiMessage> messages, int maxTokens, double temperature) throws IOException {
        if (!config.isConfigured()) {
            throw new IllegalStateException("AI is not configured");
        }

        JSONObject payload = new JSONObject();
        JSONArray messageArray = new JSONArray();
        try {
            payload.put("model", config.getModel());
            payload.put("temperature", temperature);
            payload.put("max_tokens", maxTokens);
            for (AiMessage message : messages) {
                JSONObject messageJson = new JSONObject();
                messageJson.put("role", message.getRole());
                messageJson.put("content", message.getContent());
                messageArray.put(messageJson);
            }
            payload.put("messages", messageArray);
        } catch (JSONException e) {
            throw new IOException("Failed to build AI request", e);
        }

        Request request = new Request.Builder()
                .url(config.getChatCompletionsUrl())
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(payload.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String body = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException(extractErrorMessage(response.code(), body));
            }
            return extractContent(body);
        }
    }

    private String extractContent(String body) throws IOException {
        try {
            JSONObject responseJson = new JSONObject(body);
            JSONArray choices = responseJson.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IOException("Empty AI response");
            }
            JSONObject choice = choices.optJSONObject(0);
            if (choice == null) {
                throw new IOException("Invalid AI response");
            }
            JSONObject message = choice.optJSONObject("message");
            if (message == null) {
                throw new IOException("Invalid AI response");
            }
            String content = message.optString("content", "").trim();
            if (!content.isEmpty()) {
                return content;
            }
            throw new IOException("AI returned no content");
        } catch (JSONException e) {
            throw new IOException("Failed to parse AI response", e);
        }
    }

    private String extractErrorMessage(int statusCode, String body) {
        if (body == null || body.trim().isEmpty()) {
            return "AI request failed: HTTP " + statusCode;
        }
        try {
            JSONObject jsonObject = new JSONObject(body);
            Object errorObject = jsonObject.opt("error");
            if (errorObject instanceof JSONObject) {
                JSONObject errorJson = (JSONObject) errorObject;
                String message = errorJson.optString("message", "").trim();
                if (!message.isEmpty()) {
                    return message;
                }
            }
            if (errorObject instanceof String) {
                String message = ((String) errorObject).trim();
                if (!message.isEmpty()) {
                    return message;
                }
            }
            String message = jsonObject.optString("message", "").trim();
            if (!message.isEmpty()) {
                return message;
            }
        } catch (JSONException ignored) {
        }
        return "AI request failed: HTTP " + statusCode;
    }
}
