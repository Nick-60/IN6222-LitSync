package com.in6222.litsync.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.in6222.litsync.R;
import com.in6222.litsync.ai.AiAssistant;
import com.in6222.litsync.database.AppDatabase;
import com.in6222.litsync.database.FavoritePaper;
import com.in6222.litsync.databinding.FragmentBookmarksBinding;
import com.in6222.litsync.model.PaperItem;
import com.in6222.litsync.network.RetrofitClient;
import com.in6222.litsync.repository.PaperRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookmarksFragment extends Fragment {

    private static final int MAX_VISIBLE_TAG_FILTERS = 4;

    private FragmentBookmarksBinding binding;
    private ExecutorService executor;
    private PaperRepository repository;
    private PaperAdapter adapter;
    private AiAssistant aiAssistant;
    private BookmarkMetadataStore metadataStore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<PaperItem> allBookmarkItems = new ArrayList<>();
    private String selectedGroup = "";
    private String selectedTag = "";
    private boolean filtersCollapsed = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentBookmarksBinding.inflate(inflater, container, false);
        executor = Executors.newSingleThreadExecutor();
        AppDatabase database = Room
                .databaseBuilder(requireContext().getApplicationContext(), AppDatabase.class, "litsync.db").build();
        repository = new PaperRepository(RetrofitClient.getService(), database);
        aiAssistant = AiAssistant.create(requireContext());
        metadataStore = new BookmarkMetadataStore(requireContext());
        adapter = new PaperAdapter(repository, executor, false, aiAssistant);
        adapter.setOnBookmarkMetadataChangedListener(this::refreshGroupFiltersAndList);
        adapter.setOnBookmarkTagClickListener(this::filterByTag);
        adapter.setOnBookmarkDeleteRequestListener(this::confirmDeleteBookmark);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        binding.toggleFiltersButton.setOnClickListener(v -> toggleFilters());
        binding.browseTagsButton.setOnClickListener(v -> showAllTagsDialog(buildSortedTags(getItemsMatchingGroupFilter())));
        attachSwipeToDelete();
        showLoadingState();
        loadBookmarks();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded()) {
            return;
        }
        aiAssistant = AiAssistant.create(requireContext());
        if (adapter != null) {
            adapter.setAiAssistant(aiAssistant);
        }
        loadBookmarks();
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        if (executor != null) {
            executor.shutdown();
        }
        binding = null;
    }

    private void loadBookmarks() {
        showLoadingState();
        executor.execute(() -> {
            List<FavoritePaper> favorites = repository.getAllFavorites();
            List<PaperItem> items = mapFavorites(favorites);
            mainHandler.post(() -> {
                allBookmarkItems.clear();
                allBookmarkItems.addAll(items);
                refreshGroupFiltersAndList();
            });
        });
    }

    private List<PaperItem> mapFavorites(List<FavoritePaper> favorites) {
        List<PaperItem> items = new ArrayList<>();
        if (favorites == null) {
            return items;
        }
        for (FavoritePaper favorite : favorites) {
            items.add(new PaperItem(
                    favorite.getId(),
                    favorite.getTitle(),
                    favorite.getAuthor(),
                    favorite.getSummary(),
                    favorite.getPublishedDate(),
                    favorite.getLink()));
        }
        return items;
    }

    private void attachSwipeToDelete() {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private android.graphics.Paint backgroundPaint;
            private android.graphics.Paint actionPaint;
            private android.graphics.Paint labelPaint;
            private final Drawable deleteIcon = ContextCompat.getDrawable(requireContext(),
                    android.R.drawable.ic_menu_delete);
            private int cornerRadius = -1;
            private int sideInset = -1;
            private int actionWidth = -1;
            private int actionHeight = -1;
            private int labelGap = -1;
            private int minRevealWidthForLabel = -1;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.45f;
            }

            @Override
            public void onChildDraw(
                    @NonNull Canvas c,
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    float dX,
                    float dY,
                    int actionState,
                    boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    if (backgroundPaint == null) {
                        float density = itemView.getResources().getDisplayMetrics().density;
                        sideInset = (int) (14 * density);
                        actionWidth = (int) (96 * density);
                        actionHeight = (int) (56 * density);
                        cornerRadius = actionHeight / 2;
                        labelGap = (int) (10 * density);
                        minRevealWidthForLabel = (int) (86 * density);

                        backgroundPaint = new android.graphics.Paint();
                        backgroundPaint.setColor(Color.TRANSPARENT);
                        backgroundPaint.setAntiAlias(true);

                        actionPaint = new android.graphics.Paint();
                        actionPaint.setColor(ContextCompat.getColor(itemView.getContext(), R.color.md_error));
                        actionPaint.setAntiAlias(true);

                        labelPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        labelPaint.setColor(ContextCompat.getColor(itemView.getContext(), R.color.md_onError));
                        labelPaint.setTextSize(14 * density);
                        labelPaint.setFakeBoldText(true);
                        labelPaint.setLinearText(true);
                    }

                    int right = itemView.getRight();
                    int revealWidth = Math.min((int) (-dX), actionWidth + sideInset);

                    if (revealWidth > 0) {
                        float actionRight = right - sideInset;
                        float actionLeft = actionRight - Math.min(revealWidth, actionWidth);
                        float centerY = itemView.getTop() + (itemView.getHeight() / 2f);
                        float halfHeight = actionHeight / 2f;
                        float actionTop = centerY - halfHeight;
                        float actionBottom = centerY + halfHeight;

                        android.graphics.RectF actionRect = new android.graphics.RectF(actionLeft, actionTop,
                                actionRight, actionBottom);
                        c.drawRoundRect(actionRect, cornerRadius, cornerRadius, actionPaint);

                        if (deleteIcon != null) {
                            int iconSize = Math.min(deleteIcon.getIntrinsicWidth(), deleteIcon.getIntrinsicHeight());
                            int iconTop = (int) (actionRect.centerY() - (iconSize / 2f));
                            int iconBottom = iconTop + iconSize;
                            boolean showLabel = revealWidth >= minRevealWidthForLabel;
                            if (showLabel) {
                                float labelWidth = labelPaint.measureText(getString(R.string.action_delete));
                                int iconLeft = (int) (actionRect.centerX() - ((iconSize + labelGap + labelWidth) / 2f));
                                int iconRight = iconLeft + iconSize;
                                deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                                deleteIcon.setTint(ContextCompat.getColor(itemView.getContext(), R.color.md_onError));
                                deleteIcon.draw(c);

                                android.graphics.Paint.FontMetrics fontMetrics = labelPaint.getFontMetrics();
                                float textX = iconRight + labelGap;
                                float textY = actionRect.centerY() - ((fontMetrics.ascent + fontMetrics.descent) / 2f);
                                c.drawText(getString(R.string.action_delete), textX, textY, labelPaint);
                            } else {
                                int iconLeft = (int) (actionRect.centerX() - (iconSize / 2f));
                                int iconRight = iconLeft + iconSize;
                                deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                                deleteIcon.setTint(ContextCompat.getColor(itemView.getContext(), R.color.md_onError));
                                deleteIcon.draw(c);
                            }
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                deleteBookmarkAt(position);
            }
        });
        helper.attachToRecyclerView(binding.recyclerView);
    }

    private void confirmDeleteBookmark(PaperItem item) {
        if (binding == null || item == null) {
            return;
        }
        int position = findBookmarkPosition(item);
        if (position < 0) {
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.title_delete_bookmark)
                .setMessage(R.string.message_bookmark_delete_confirm)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteBookmarkAt(position))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int findBookmarkPosition(PaperItem target) {
        String targetKey = dedupeKey(target);
        for (int index = 0; index < allBookmarkItems.size(); index++) {
            if (targetKey.equals(dedupeKey(allBookmarkItems.get(index)))) {
                return index;
            }
        }
        return -1;
    }

    private String dedupeKey(PaperItem item) {
        String link = item.getLink() == null ? "" : item.getLink().trim();
        if (!link.isEmpty()) {
            return link;
        }
        String title = item.getTitle() == null ? "" : item.getTitle().trim();
        String publishedDate = item.getPublishedDate() == null ? "" : item.getPublishedDate().trim();
        return title + "|" + publishedDate;
    }

    private void deleteBookmarkAt(int position) {
        if (binding == null || position < 0 || position >= allBookmarkItems.size()) {
            return;
        }
        PaperItem removed = allBookmarkItems.get(position);
        FavoritePaper favorite = toFavorite(removed);
        allBookmarkItems.remove(position);
        refreshGroupFiltersAndList();
        updateEmptyState();
        executor.execute(() -> repository.deleteFavorite(favorite));
        Snackbar.make(binding.getRoot(), getString(R.string.message_removed_bookmark), Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, v -> {
                    int restoreIndex = Math.min(position, allBookmarkItems.size());
                    allBookmarkItems.add(restoreIndex, removed);
                    refreshGroupFiltersAndList();
                    executor.execute(() -> repository.saveFavorite(favorite));
                })
                .show();
    }

    private void updateEmptyState() {
        if (binding == null) {
            return;
        }
        boolean isEmpty = adapter.getItemCount() == 0;
        binding.statusCard.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.progressBar.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty) {
            binding.emptyText.setText(R.string.status_empty_bookmarks_guidance);
        }
    }

    private FavoritePaper toFavorite(PaperItem item) {
        FavoritePaper favorite = new FavoritePaper();
        favorite.setId(item.getId());
        favorite.setTitle(item.getTitle());
        favorite.setAuthor(item.getAuthor());
        favorite.setSummary(item.getSummary());
        favorite.setPublishedDate(item.getPublishedDate());
        favorite.setLink(item.getLink());
        return favorite;
    }

    private void refreshGroupFiltersAndList() {
        if (binding == null) {
            return;
        }
        renderGroupFilter();
        renderTagChips();
        updateFilterSectionUi();
        applyFilters();
    }

    private void renderGroupFilter() {
        if (binding == null) {
            return;
        }
        boolean show = !allBookmarkItems.isEmpty();
        binding.groupFilterLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            binding.groupFilterInput.setText("", false);
            return;
        }
        List<String> groups = metadataStore.getGroups(allBookmarkItems);
        if (!selectedGroup.isEmpty() && !groups.contains(selectedGroup)) {
            selectedGroup = "";
        }
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.action_all_groups));
        options.addAll(groups);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line,
                options);
        binding.groupFilterInput.setAdapter(adapter);
        binding.groupFilterInput.setThreshold(0);
        binding.groupFilterInput.setOnClickListener(v -> binding.groupFilterInput.showDropDown());
        binding.groupFilterInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.groupFilterInput.showDropDown();
            }
        });
        binding.groupFilterInput.setOnItemClickListener(
                (parent, view, position, id) -> applyGroupFilter(position == 0 ? "" : options.get(position), true));
        binding.groupFilterInput
                .setText(selectedGroup.isEmpty() ? getString(R.string.action_all_groups) : selectedGroup, false);
    }

    private void renderTagChips() {
        if (binding == null) {
            return;
        }
        List<String> tags = buildSortedTags(getItemsMatchingGroupFilter());
        if (!selectedTag.isEmpty() && !tags.contains(selectedTag)) {
            selectedTag = "";
        }
        binding.tagFilterChipGroup.removeAllViews();
        if (tags.isEmpty()) {
            return;
        }
        addTagChip(getString(R.string.action_all_tags), "");
        List<String> visibleTags = buildVisibleTags(tags);
        for (String tag : visibleTags) {
            addTagChip(tag, tag);
        }
    }

    private void addTagChip(String label, String tagValue) {
        if (binding == null) {
            return;
        }
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(tagValue.equals(selectedTag));
        styleFilterChip(chip);
        chip.setOnClickListener(v -> applyTagFilter(tagValue, true));
        binding.tagFilterChipGroup.addView(chip);
    }

    private void applyFilters() {
        if (binding == null) {
            return;
        }
        List<PaperItem> filteredItems = new ArrayList<>();
        for (PaperItem item : allBookmarkItems) {
            String group = metadataStore.getGroup(item);
            String tags = metadataStore.getTags(item);
            boolean matchesGroup = selectedGroup.isEmpty() || selectedGroup.equals(group);
            boolean matchesTag = selectedTag.isEmpty() || containsTag(tags, selectedTag);
            if (matchesGroup && matchesTag) {
                filteredItems.add(item);
            }
        }
        adapter.setItems(filteredItems);
        updateEmptyState();
    }

    private List<PaperItem> getItemsMatchingGroupFilter() {
        List<PaperItem> filteredItems = new ArrayList<>();
        for (PaperItem item : allBookmarkItems) {
            String group = metadataStore.getGroup(item);
            if (selectedGroup.isEmpty() || selectedGroup.equals(group)) {
                filteredItems.add(item);
            }
        }
        return filteredItems;
    }

    private boolean containsTag(String tags, String targetTag) {
        if (tags == null || tags.trim().isEmpty() || targetTag == null || targetTag.trim().isEmpty()) {
            return false;
        }
        for (String part : tags.split(",")) {
            if (targetTag.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildSortedTags(List<PaperItem> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> displayValues = new LinkedHashMap<>();
        for (PaperItem item : items) {
            String rawTags = metadataStore.getTags(item);
            if (rawTags == null || rawTags.trim().isEmpty()) {
                continue;
            }
            for (String part : rawTags.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String normalized = trimmed.toLowerCase(Locale.US);
                counts.put(normalized, counts.getOrDefault(normalized, 0) + 1);
                if (!displayValues.containsKey(normalized)) {
                    displayValues.put(normalized, trimmed);
                }
            }
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .reversed()
                .thenComparing(entry -> displayValues.get(entry.getKey()), String.CASE_INSENSITIVE_ORDER));
        List<String> tags = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : entries) {
            tags.add(displayValues.get(entry.getKey()));
        }
        return tags;
    }

    private List<String> buildVisibleTags(List<String> tags) {
        List<String> visible = new ArrayList<>();
        if (!selectedTag.isEmpty()) {
            visible.add(selectedTag);
        }
        for (String tag : tags) {
            if (containsIgnoreCase(visible, tag)) {
                continue;
            }
            if (visible.size() >= MAX_VISIBLE_TAG_FILTERS) {
                break;
            }
            visible.add(tag);
        }
        return visible;
    }

    private void showAllTagsDialog(List<String> tags) {
        if (binding == null) {
            return;
        }
        if (tags == null || tags.isEmpty()) {
            Snackbar.make(binding.getRoot(), getString(R.string.message_bookmark_no_tags_available), Snackbar.LENGTH_SHORT)
                    .show();
            return;
        }
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.action_all_tags));
        options.addAll(tags);
        String[] labels = options.toArray(new String[0]);
        int selectedIndex = 0;
        if (!selectedTag.isEmpty()) {
            for (int index = 1; index < options.size(); index++) {
                if (selectedTag.equalsIgnoreCase(options.get(index))) {
                    selectedIndex = index;
                    break;
                }
            }
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.title_choose_tag_filter)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    applyTagFilter(which == 0 ? "" : options.get(which), true);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        if (candidate == null) {
            return false;
        }
        for (String value : values) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private void filterByTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return;
        }
        applyTagFilter(tag.trim(), true);
    }

    private void applyGroupFilter(String groupValue, boolean announceChange) {
        selectedGroup = groupValue == null ? "" : groupValue.trim();
        renderGroupFilter();
        renderTagChips();
        updateFilterSectionUi();
        applyFilters();
        if (binding != null) {
            binding.recyclerView.scrollToPosition(0);
            if (announceChange) {
                int messageId = selectedGroup.isEmpty()
                        ? R.string.message_bookmark_group_filter_all
                        : R.string.message_bookmark_group_filter_applied;
                String message = selectedGroup.isEmpty()
                        ? getString(messageId)
                        : getString(messageId, selectedGroup);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void applyTagFilter(String tagValue, boolean announceChange) {
        selectedTag = tagValue == null ? "" : tagValue.trim();
        renderTagChips();
        updateFilterSectionUi();
        applyFilters();
        if (binding != null) {
            binding.recyclerView.scrollToPosition(0);
            if (announceChange) {
                int messageId = selectedTag.isEmpty()
                        ? R.string.message_bookmark_tag_filter_all
                        : R.string.message_bookmark_tag_filter_applied;
                String message = selectedTag.isEmpty()
                        ? getString(messageId)
                        : getString(messageId, selectedTag);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void styleFilterChip(Chip chip) {
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth((int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.55f));
    }

    private void toggleFilters() {
        filtersCollapsed = !filtersCollapsed;
        renderGroupFilter();
        renderTagChips();
        updateFilterSectionUi();
    }

    private void updateFilterSectionUi() {
        if (binding == null) {
            return;
        }
        boolean hasItems = !allBookmarkItems.isEmpty();
        binding.filterHeaderRow.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        boolean showFilterContent = hasItems && !filtersCollapsed;
        binding.groupFilterLayout.setVisibility(showFilterContent ? View.VISIBLE : View.GONE);
        boolean hasTagFilters = binding.tagFilterChipGroup.getChildCount() > 0;
        binding.tagFilterHeaderRow.setVisibility(showFilterContent && hasTagFilters ? View.VISIBLE : View.GONE);
        binding.tagFilterScroll.setVisibility(showFilterContent && hasTagFilters ? View.VISIBLE : View.GONE);
        binding.browseTagsButton.setEnabled(hasTagFilters);
        binding.toggleFiltersButton
                .setText(filtersCollapsed ? R.string.action_show_filters : R.string.action_hide_filters);
        binding.filterSummaryText.setText(getString(
                R.string.text_bookmark_filter_summary,
                selectedGroup.isEmpty() ? getString(R.string.action_all_groups) : selectedGroup,
                selectedTag.isEmpty() ? getString(R.string.action_all_tags) : selectedTag));
    }

    private void showLoadingState() {
        if (binding == null) {
            return;
        }
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.emptyText.setVisibility(View.VISIBLE);
        binding.emptyText.setText(R.string.status_bookmarks_loading);
    }
}
