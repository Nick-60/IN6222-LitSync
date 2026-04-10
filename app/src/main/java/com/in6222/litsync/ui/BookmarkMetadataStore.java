package com.in6222.litsync.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.in6222.litsync.model.PaperItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BookmarkMetadataStore {

    private static final String PREFS_NAME = "bookmark_metadata";
    private static final String KEY_NOTE_PREFIX = "note_";
    private static final String KEY_TAGS_PREFIX = "tags_";
    private static final String KEY_GROUP_PREFIX = "group_";

    private final SharedPreferences preferences;

    public BookmarkMetadataStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getNote(PaperItem item) {
        return preferences.getString(KEY_NOTE_PREFIX + getMetadataKey(item), "").trim();
    }

    public String getTags(PaperItem item) {
        return preferences.getString(KEY_TAGS_PREFIX + getMetadataKey(item), "").trim();
    }

    public String getGroup(PaperItem item) {
        return preferences.getString(KEY_GROUP_PREFIX + getMetadataKey(item), "").trim();
    }

    public void saveNote(PaperItem item, String value) {
        save(KEY_NOTE_PREFIX, item, value);
    }

    public void saveTags(PaperItem item, String value) {
        save(KEY_TAGS_PREFIX, item, value);
    }

    public void saveGroup(PaperItem item, String value) {
        save(KEY_GROUP_PREFIX, item, value);
    }

    public List<String> getGroups(List<PaperItem> items) {
        Set<String> groups = new LinkedHashSet<>();
        if (items == null) {
            return new ArrayList<>();
        }
        for (PaperItem item : items) {
            String group = getGroup(item);
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }
        return new ArrayList<>(groups);
    }

    public List<String> getTags(List<PaperItem> items) {
        Set<String> tags = new LinkedHashSet<>();
        if (items == null) {
            return new ArrayList<>();
        }
        for (PaperItem item : items) {
            String rawTags = getTags(item);
            if (rawTags.isEmpty()) {
                continue;
            }
            for (String part : rawTags.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    tags.add(trimmed);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    public List<String> getAllGroups() {
        Set<String> groups = new LinkedHashSet<>();
        Map<String, ?> allValues = preferences.getAll();
        for (Map.Entry<String, ?> entry : allValues.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(KEY_GROUP_PREFIX)) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String trimmed = ((String) value).trim();
            if (!trimmed.isEmpty()) {
                groups.add(trimmed);
            }
        }
        return new ArrayList<>(groups);
    }

    public List<String> getAllTags() {
        Set<String> tags = new LinkedHashSet<>();
        Map<String, ?> allValues = preferences.getAll();
        for (Map.Entry<String, ?> entry : allValues.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(KEY_TAGS_PREFIX)) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String rawTags = ((String) value).trim();
            if (rawTags.isEmpty()) {
                continue;
            }
            for (String part : rawTags.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    tags.add(trimmed);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private void save(String prefix, PaperItem item, String value) {
        String key = prefix + getMetadataKey(item);
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            preferences.edit().remove(key).apply();
            return;
        }
        preferences.edit().putString(key, trimmed).apply();
    }

    private String getMetadataKey(PaperItem item) {
        String link = item.getLink() == null ? "" : item.getLink().trim();
        if (!link.isEmpty()) {
            return Uri.encode(link);
        }
        String title = item.getTitle() == null ? "" : item.getTitle().trim();
        String publishedDate = item.getPublishedDate() == null ? "" : item.getPublishedDate().trim();
        return Uri.encode(title + "|" + publishedDate);
    }
}
