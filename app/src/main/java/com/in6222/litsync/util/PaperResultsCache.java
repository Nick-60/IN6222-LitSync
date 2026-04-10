package com.in6222.litsync.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.in6222.litsync.model.PaperItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PaperResultsCache {

    private static final String PREFS = "paper_results_cache";

    private final SharedPreferences preferences;

    public PaperResultsCache(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(String key, List<PaperItem> items, int currentPage, int totalPages, int totalResults) {
        if (key == null || key.trim().isEmpty() || items == null || items.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("currentPage", currentPage);
            root.put("totalPages", totalPages);
            root.put("totalResults", totalResults);
            JSONArray array = new JSONArray();
            for (PaperItem item : items) {
                if (item == null) {
                    continue;
                }
                JSONObject jsonItem = new JSONObject();
                jsonItem.put("id", item.getId());
                jsonItem.put("title", safe(item.getTitle()));
                jsonItem.put("author", safe(item.getAuthor()));
                jsonItem.put("summary", safe(item.getSummary()));
                jsonItem.put("publishedDate", safe(item.getPublishedDate()));
                jsonItem.put("link", safe(item.getLink()));
                array.put(jsonItem);
            }
            root.put("items", array);
            preferences.edit().putString(key, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public CachedPaperResults load(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        String raw = preferences.getString(key, null);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray array = root.optJSONArray("items");
            if (array == null || array.length() == 0) {
                return null;
            }
            List<PaperItem> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject jsonItem = array.optJSONObject(i);
                if (jsonItem == null) {
                    continue;
                }
                items.add(new PaperItem(
                        jsonItem.optInt("id", 0),
                        jsonItem.optString("title", ""),
                        jsonItem.optString("author", ""),
                        jsonItem.optString("summary", ""),
                        jsonItem.optString("publishedDate", ""),
                        jsonItem.optString("link", "")
                ));
            }
            if (items.isEmpty()) {
                return null;
            }
            return new CachedPaperResults(
                    items,
                    root.optInt("currentPage", 1),
                    root.optInt("totalPages", 1),
                    root.optInt("totalResults", items.size())
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static class CachedPaperResults {
        private final List<PaperItem> items;
        private final int currentPage;
        private final int totalPages;
        private final int totalResults;

        public CachedPaperResults(List<PaperItem> items, int currentPage, int totalPages, int totalResults) {
            this.items = items;
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.totalResults = totalResults;
        }

        public List<PaperItem> getItems() {
            return items;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public int getTotalResults() {
            return totalResults;
        }
    }
}
