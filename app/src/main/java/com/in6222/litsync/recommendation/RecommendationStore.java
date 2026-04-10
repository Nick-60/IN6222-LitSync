package com.in6222.litsync.recommendation;

import android.content.Context;
import android.content.SharedPreferences;

import com.in6222.litsync.model.PaperItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RecommendationStore {

    private static final String PREFS_NAME = "daily_recommendations";
    private static final String KEY_RECOMMENDATIONS = "recommendations";
    private static final String KEY_GENERATED_AT = "generated_at";
    private static final String KEY_NOTIFIED_LINKS = "notified_links";

    private final SharedPreferences preferences;

    public RecommendationStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveRecommendations(List<RecommendedPaper> recommendations) {
        JSONArray jsonArray = new JSONArray();
        if (recommendations != null) {
            for (RecommendedPaper recommendation : recommendations) {
                try {
                    PaperItem paperItem = recommendation.getPaperItem();
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("id", paperItem.getId());
                    jsonObject.put("title", paperItem.getTitle());
                    jsonObject.put("author", paperItem.getAuthor());
                    jsonObject.put("summary", paperItem.getSummary());
                    jsonObject.put("publishedDate", paperItem.getPublishedDate());
                    jsonObject.put("link", paperItem.getLink());
                    jsonObject.put("primaryInterest", recommendation.getPrimaryInterest());
                    jsonObject.put("reason", recommendation.getRecommendationReason());
                    jsonObject.put("score", recommendation.getScore());
                    jsonArray.put(jsonObject);
                } catch (Exception ignored) {
                }
            }
        }
        preferences.edit()
                .putString(KEY_RECOMMENDATIONS, jsonArray.toString())
                .putLong(KEY_GENERATED_AT, System.currentTimeMillis())
                .apply();
    }

    public List<RecommendedPaper> loadRecommendations() {
        List<RecommendedPaper> recommendations = new ArrayList<>();
        String rawJson = preferences.getString(KEY_RECOMMENDATIONS, "[]");
        try {
            JSONArray jsonArray = new JSONArray(rawJson);
            for (int index = 0; index < jsonArray.length(); index++) {
                JSONObject jsonObject = jsonArray.getJSONObject(index);
                PaperItem paperItem = new PaperItem(
                        jsonObject.optInt("id", 0),
                        jsonObject.optString("title", ""),
                        jsonObject.optString("author", ""),
                        jsonObject.optString("summary", ""),
                        jsonObject.optString("publishedDate", ""),
                        jsonObject.optString("link", "")
                );
                recommendations.add(new RecommendedPaper(
                        paperItem,
                        jsonObject.optString("primaryInterest", ""),
                        jsonObject.optString("reason", ""),
                        jsonObject.optInt("score", 0)
                ));
            }
        } catch (Exception ignored) {
        }
        return recommendations;
    }

    public long getGeneratedAt() {
        return preferences.getLong(KEY_GENERATED_AT, 0L);
    }

    public Set<String> getNotifiedLinks() {
        return new LinkedHashSet<>(preferences.getStringSet(KEY_NOTIFIED_LINKS, new LinkedHashSet<>()));
    }

    public void addNotifiedRecommendations(List<RecommendedPaper> recommendations) {
        Set<String> notifiedLinks = getNotifiedLinks();
        if (recommendations != null) {
            for (RecommendedPaper recommendation : recommendations) {
                String link = recommendation.getPaperItem().getLink();
                if (link != null && !link.trim().isEmpty()) {
                    notifiedLinks.add(link.trim());
                }
            }
        }
        preferences.edit().putStringSet(KEY_NOTIFIED_LINKS, notifiedLinks).apply();
    }
}
