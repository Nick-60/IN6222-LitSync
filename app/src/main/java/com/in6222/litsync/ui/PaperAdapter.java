package com.in6222.litsync.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.in6222.litsync.R;
import com.in6222.litsync.ai.AiAssistant;
import com.in6222.litsync.database.FavoritePaper;
import com.in6222.litsync.databinding.ItemPaperBinding;
import com.in6222.litsync.model.PaperItem;
import com.in6222.litsync.repository.PaperRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class PaperAdapter extends RecyclerView.Adapter<PaperAdapter.PaperViewHolder> {

    private static final int MAX_VISIBLE_KEYWORDS = 3;
    private static final int MAX_VISIBLE_BOOKMARK_TAGS = 3;
    private static final Set<String> KEYWORD_STOP_WORDS = new HashSet<>(Arrays.asList(
            "about", "after", "also", "among", "and", "approach", "based", "between", "from",
            "have", "into", "more", "paper", "papers", "result", "results", "show", "shows",
            "that", "their", "these", "this", "using", "with", "within", "without", "which",
            "where", "while", "been", "being", "such", "than", "then", "they", "them", "over",
            "under", "into", "onto", "your", "ours", "the", "for", "are", "was", "were",
            "will", "can", "may", "our", "its", "via", "new", "study", "method", "methods"
    ));
    private static final List<String> DEFAULT_BOOKMARK_GROUPS = Arrays.asList(
            "To Read", "Reading", "Important", "Survey", "Experiment"
    );

    private final List<PaperItem> items = new ArrayList<>();
    private final PaperRepository repository;
    private final ExecutorService executor;
    private final boolean enableBookmarking;
    private AiAssistant aiAssistant;
    private OnKeywordClickListener keywordClickListener;
    private OnBookmarkMetadataChangedListener bookmarkMetadataChangedListener;
    private OnBookmarkTagClickListener bookmarkTagClickListener;
    private OnBookmarkDeleteRequestListener bookmarkDeleteRequestListener;
    private final Map<String, String> recommendationReasons = new LinkedHashMap<>();
    private final Map<String, String> recommendationPrimaryInterests = new LinkedHashMap<>();
    private final Map<String, Integer> recommendationScores = new LinkedHashMap<>();
    private final Set<String> highlightedRecommendationKeys = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> expandedSummaries = new HashSet<>();
    private final Map<String, String> latestAiSummaries = new LinkedHashMap<>();
    private final Set<String> loadingAiSummaries = new HashSet<>();
    private String pinnedKeyword = "";

    public PaperAdapter(PaperRepository repository, ExecutorService executor, boolean enableBookmarking, AiAssistant aiAssistant) {
        this.repository = repository;
        this.executor = executor;
        this.enableBookmarking = enableBookmarking;
        this.aiAssistant = aiAssistant;
    }

    public void setAiAssistant(AiAssistant aiAssistant) {
        this.aiAssistant = aiAssistant;
    }

    public void setOnKeywordClickListener(OnKeywordClickListener keywordClickListener) {
        this.keywordClickListener = keywordClickListener;
    }

    public void setOnBookmarkMetadataChangedListener(OnBookmarkMetadataChangedListener bookmarkMetadataChangedListener) {
        this.bookmarkMetadataChangedListener = bookmarkMetadataChangedListener;
    }

    public void setOnBookmarkTagClickListener(OnBookmarkTagClickListener bookmarkTagClickListener) {
        this.bookmarkTagClickListener = bookmarkTagClickListener;
    }

    public void setOnBookmarkDeleteRequestListener(OnBookmarkDeleteRequestListener bookmarkDeleteRequestListener) {
        this.bookmarkDeleteRequestListener = bookmarkDeleteRequestListener;
    }

    public void setPinnedKeyword(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (pinnedKeyword.equals(normalizedKeyword)) {
            return;
        }
        pinnedKeyword = normalizedKeyword;
        notifyDataSetChanged();
    }

    public void setRecommendationReasons(Map<String, String> reasons) {
        recommendationReasons.clear();
        if (reasons != null) {
            recommendationReasons.putAll(reasons);
        }
        notifyDataSetChanged();
    }

    public void setRecommendationMetadata(
            Map<String, String> reasons,
            Map<String, String> primaryInterests,
            Map<String, Integer> scores
    ) {
        recommendationReasons.clear();
        recommendationPrimaryInterests.clear();
        recommendationScores.clear();
        highlightedRecommendationKeys.clear();
        if (reasons != null) {
            recommendationReasons.putAll(reasons);
        }
        if (primaryInterests != null) {
            recommendationPrimaryInterests.putAll(primaryInterests);
        }
        if (scores != null) {
            recommendationScores.putAll(scores);
        }
        notifyDataSetChanged();
    }

    public void setItems(List<PaperItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void appendItems(List<PaperItem> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            return;
        }
        int start = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    public PaperItem getItem(int position) {
        return items.get(position);
    }

    public void removeItem(int position) {
        items.remove(position);
        notifyItemRemoved(position);
    }

    public void insertItem(int position, PaperItem item) {
        items.add(position, item);
        notifyItemInserted(position);
    }

    @NonNull
    @Override
    public PaperViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemPaperBinding binding = ItemPaperBinding.inflate(inflater, parent, false);
        return new PaperViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PaperViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class PaperViewHolder extends RecyclerView.ViewHolder {

        private final ItemPaperBinding binding;
        private final GestureDetectorCompat gestureDetector;
        private PaperItem currentItem;

        PaperViewHolder(ItemPaperBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            gestureDetector = new GestureDetectorCompat(binding.getRoot().getContext(), new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (enableBookmarking && repository != null && executor != null && currentItem != null) {
                        FavoritePaper favorite = new FavoritePaper();
                        favorite.setTitle(currentItem.getTitle());
                        favorite.setAuthor(currentItem.getAuthor());
                        favorite.setSummary(currentItem.getSummary());
                        favorite.setPublishedDate(currentItem.getPublishedDate());
                        favorite.setLink(currentItem.getLink());
                        executor.execute(() -> {
                            long id = repository.saveFavorite(favorite);
                            favorite.setId((int) id);
                            mainHandler.post(() -> {
                                View root = binding.getRoot();
                                Snackbar.make(root, R.string.message_added_bookmark, Snackbar.LENGTH_LONG)
                                        .setAction(R.string.action_undo, v -> executor.execute(() -> repository.deleteFavorite(favorite)))
                                        .show();
                            });
                        });
                    }
                    return true;
                }
            });
            binding.getRoot().setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return false;
            });
        }

        void bind(PaperItem item) {
            currentItem = item;
            String itemKey = getSummaryKey(item);
            binding.titleText.setText(item.getTitle());
            binding.authorText.setText(item.getAuthor());
            binding.summaryText.setText(item.getSummary());
            binding.publishedText.setText(item.getPublishedDate());
            bindCardPresentation(item);
            bindRecommendationReason(item);
            bindRecommendationScore(item);
            bindBookmarkMetadata(item);
            binding.getRoot().setOnClickListener(v -> toggleRecommendationHighlight(itemKey, item));

            binding.readButton.setOnClickListener(v -> {
                if (item.getLink() == null || item.getLink().isEmpty()) {
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getLink()));
                v.getContext().startActivity(intent);
            });

            binding.aiButton.setOnClickListener(v -> requestAiSummary(item));
            binding.toggleSummaryButton.setOnClickListener(v -> toggleSummary(item));
            binding.moreButton.setOnClickListener(v -> showMoreActions(item));
            bindAiSummaryState(itemKey);
        }

        private void bindCardPresentation(PaperItem item) {
            if (enableBookmarking) {
                binding.authorText.setVisibility(View.VISIBLE);
                bindSummaryState(item);
                bindKeywordChips(item);
                return;
            }
            binding.authorText.setVisibility(View.GONE);
            binding.summaryText.setVisibility(View.GONE);
            binding.toggleSummaryButton.setVisibility(View.GONE);
            binding.keywordChipGroup.setVisibility(View.GONE);
            binding.keywordChipGroup.removeAllViews();
        }

        private void requestAiSummary(PaperItem item) {
            requestAiSummary(item, false);
        }

        private void requestAiSummary(PaperItem item, boolean forceRefresh) {
            String cacheKey = getSummaryKey(item);
            if (loadingAiSummaries.contains(cacheKey)) {
                return;
            }
            if (!forceRefresh) {
                String cachedSummary = latestAiSummaries.get(cacheKey);
                if (cachedSummary != null && !cachedSummary.trim().isEmpty()) {
                    showAiSummaryDialog(item, cachedSummary);
                    return;
                }
            }
            if (aiAssistant == null || !aiAssistant.isAvailable()) {
                Snackbar.make(binding.getRoot(), R.string.message_ai_unavailable, Snackbar.LENGTH_LONG).show();
                return;
            }
            updateAiSummaryLoadingState(cacheKey, true);
            Snackbar.make(
                    binding.getRoot(),
                    forceRefresh ? R.string.message_ai_regenerating_summary : R.string.message_ai_generating_summary,
                    Snackbar.LENGTH_SHORT
            ).show();
            executor.execute(() -> {
                try {
                    String summary = forceRefresh
                            ? aiAssistant.regeneratePaperSummary(item)
                            : aiAssistant.summarizePaper(item);
                    if (summary == null || summary.trim().isEmpty()) {
                        throw new IllegalStateException(binding.getRoot().getContext().getString(R.string.message_ai_empty_response));
                    }
                    mainHandler.post(() -> {
                        updateAiSummaryLoadingState(cacheKey, false);
                        latestAiSummaries.put(cacheKey, summary);
                        showAiSummaryDialog(item, summary);
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        updateAiSummaryLoadingState(cacheKey, false);
                        String message = e.getMessage();
                        if (message == null || message.trim().isEmpty()) {
                            message = binding.getRoot().getContext().getString(R.string.message_ai_request_failed);
                        }
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                    });
                }
            });
        }

        private void showAiSummaryDialog(PaperItem item, String summary) {
            Context context = binding.getRoot().getContext();
            String formattedSummary = summary == null ? "" : summary.trim();
            ScrollView scrollView = new ScrollView(context);
            int padding = dp(context, 20);
            scrollView.setPadding(padding, dp(context, 4), padding, 0);
            LinearLayout summaryContainer = new LinearLayout(context);
            summaryContainer.setOrientation(LinearLayout.VERTICAL);
            summaryContainer.setPadding(0, 0, 0, dp(context, 8));
            addSummarySections(context, summaryContainer, formattedSummary);
            scrollView.addView(summaryContainer);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setView(scrollView)
                    .setPositiveButton(R.string.action_copy, (d, which) -> copyText(formattedSummary))
                    .setNegativeButton(R.string.action_share, (d, which) -> shareText(formattedSummary))
                    .setNeutralButton(R.string.action_regenerate, (d, which) -> requestAiSummary(item, true))
                    .create();
            dialog.setCustomTitle(createSummaryDialogTitle(context, dialog, item));
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }

        private View createSummaryDialogTitle(Context context, AlertDialog dialog, PaperItem item) {
            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int horizontalPadding = dp(context, 20);
            int topPadding = dp(context, 18);
            titleRow.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);

            LinearLayout titleColumn = new LinearLayout(context);
            titleColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams titleColumnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleColumn.setLayoutParams(titleColumnParams);

            TextView titleView = new TextView(context);
            titleView.setText(R.string.title_ai_summary);
            titleView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleColumn.addView(titleView);

            TextView subtitleView = new TextView(context);
            subtitleView.setText(item.getTitle());
            subtitleView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            subtitleView.setMaxLines(2);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.topMargin = dp(context, 4);
            subtitleView.setLayoutParams(subtitleParams);
            titleColumn.addView(subtitleView);

            TextView closeView = new TextView(context);
            closeView.setText("✕");
            closeView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            closeView.setContentDescription(context.getString(R.string.action_close));
            int closePadding = dp(context, 8);
            closeView.setPadding(closePadding, closePadding, closePadding, closePadding);
            closeView.setOnClickListener(v -> dialog.dismiss());

            titleRow.addView(titleColumn);
            titleRow.addView(closeView);
            return titleRow;
        }

        private void addSummarySections(Context context, LinearLayout container, String summary) {
            String[] blocks = summary.split("\\n\\s*\\n");
            int addedSections = 0;
            for (String block : blocks) {
                String trimmedBlock = block == null ? "" : block.trim();
                if (trimmedBlock.isEmpty()) {
                    continue;
                }
                String[] lines = trimmedBlock.split("\\n", 2);
                if (lines.length < 2) {
                    continue;
                }
                TextView sectionTitle = new TextView(context);
                sectionTitle.setText(lines[0].trim());
                sectionTitle.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
                sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
                if (addedSections > 0) {
                    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    titleParams.topMargin = dp(context, 18);
                    sectionTitle.setLayoutParams(titleParams);
                }
                container.addView(sectionTitle);

                TextView sectionBody = new TextView(context);
                sectionBody.setText(lines[1].trim());
                sectionBody.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                sectionBody.setLineSpacing(0f, 1.18f);
                sectionBody.setTextIsSelectable(true);
                LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                bodyParams.topMargin = dp(context, 6);
                sectionBody.setLayoutParams(bodyParams);
                container.addView(sectionBody);
                addedSections++;
            }

            if (addedSections > 0) {
                return;
            }

            TextView summaryText = new TextView(context);
            summaryText.setText(summary);
            summaryText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            summaryText.setLineSpacing(0f, 1.18f);
            summaryText.setTextIsSelectable(true);
            container.addView(summaryText);
        }

        private void showMoreActions(PaperItem item) {
            PopupMenu popupMenu = new PopupMenu(binding.getRoot().getContext(), binding.moreButton);
            if (enableBookmarking) {
                popupMenu.getMenu().add(0, 1, 0, R.string.action_bookmark);
            } else {
                popupMenu.getMenu().add(0, 2, 0, R.string.action_edit_notes);
                popupMenu.getMenu().add(0, 5, 1, R.string.action_delete);
            }
            popupMenu.getMenu().add(0, 3, 2, R.string.action_share);
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                int itemId = menuItem.getItemId();
                if (itemId == 1) {
                    addBookmark(item);
                    return true;
                }
                if (itemId == 2) {
                    editBookmarkMetadata(item);
                    return true;
                }
                if (itemId == 3) {
                    sharePaper(item);
                    return true;
                }
                if (itemId == 5) {
                    if (bookmarkDeleteRequestListener != null) {
                        bookmarkDeleteRequestListener.onBookmarkDeleteRequested(item);
                    }
                    return true;
                }
                return false;
            });
            popupMenu.show();
        }

        private void addBookmark(PaperItem item) {
            if (!enableBookmarking || repository == null || executor == null) {
                return;
            }
            FavoritePaper favorite = new FavoritePaper();
            favorite.setTitle(item.getTitle());
            favorite.setAuthor(item.getAuthor());
            favorite.setSummary(item.getSummary());
            favorite.setPublishedDate(item.getPublishedDate());
            favorite.setLink(item.getLink());
            executor.execute(() -> {
                long id = repository.saveFavorite(favorite);
                favorite.setId((int) id);
                mainHandler.post(() -> Snackbar.make(binding.getRoot(), R.string.message_added_bookmark, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo, v -> executor.execute(() -> repository.deleteFavorite(favorite)))
                        .show());
            });
        }

        private void sharePaper(PaperItem item) {
            String link = item.getLink() == null ? "" : item.getLink();
            String text = item.getTitle() + "\n" + link;
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            binding.getRoot().getContext().startActivity(Intent.createChooser(intent, binding.getRoot().getContext().getString(R.string.action_share)));
        }

        private void copyText(String text) {
            Context context = binding.getRoot().getContext();
            ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null) {
                Snackbar.make(binding.getRoot(), R.string.message_copy_failed, Snackbar.LENGTH_SHORT).show();
                return;
            }
            clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.title_ai_summary), text));
            Snackbar.make(binding.getRoot(), R.string.message_copied, Snackbar.LENGTH_SHORT).show();
        }

        private void shareText(String text) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            binding.getRoot().getContext().startActivity(Intent.createChooser(intent, binding.getRoot().getContext().getString(R.string.action_share)));
        }

        private void bindSummaryState(PaperItem item) {
            String summary = item.getSummary() == null ? "" : item.getSummary().trim();
            if (summary.length() <= 180) {
                binding.toggleSummaryButton.setVisibility(View.GONE);
                binding.summaryText.setMaxLines(Integer.MAX_VALUE);
                binding.summaryText.setEllipsize(null);
                return;
            }

            binding.toggleSummaryButton.setVisibility(View.VISIBLE);
            boolean isExpanded = expandedSummaries.contains(getSummaryKey(item));
            binding.summaryText.setMaxLines(isExpanded ? Integer.MAX_VALUE : 3);
            binding.summaryText.setEllipsize(isExpanded ? null : android.text.TextUtils.TruncateAt.END);
            binding.toggleSummaryButton.setText(isExpanded ? R.string.action_show_less : R.string.action_show_more);
        }

        private void toggleSummary(PaperItem item) {
            String key = getSummaryKey(item);
            if (expandedSummaries.contains(key)) {
                expandedSummaries.remove(key);
            } else {
                expandedSummaries.add(key);
            }
            bindSummaryState(item);
        }

        private void bindAiSummaryState(String itemKey) {
            boolean isLoading = loadingAiSummaries.contains(itemKey);
            binding.aiButton.clearAnimation();
            binding.aiButton.animate().cancel();
            binding.aiButton.setAlpha(1f);
            binding.aiButton.setEnabled(true);
            binding.aiButton.setText(isLoading ? R.string.action_ai_summary_loading : R.string.action_ai_summary);
            int strokeWidth = dp(binding.getRoot().getContext(), isLoading ? 2 : 1);
            binding.aiButton.setStrokeWidth(strokeWidth);
            binding.aiButton.setStrokeColorResource(isLoading ? R.color.md_primary : R.color.md_outline);
            binding.aiButton.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.md_primary));
            if (isLoading) {
                ObjectAnimator animator = ObjectAnimator.ofFloat(binding.aiButton, View.ALPHA, 1f, 0.45f);
                animator.setDuration(650);
                animator.setRepeatMode(ValueAnimator.REVERSE);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.start();
            }
        }

        private void updateAiSummaryLoadingState(String itemKey, boolean isLoading) {
            if (isLoading) {
                loadingAiSummaries.add(itemKey);
            } else {
                loadingAiSummaries.remove(itemKey);
            }
            if (currentItem != null && itemKey.equals(getSummaryKey(currentItem))) {
                bindAiSummaryState(itemKey);
                return;
            }
            int position = findItemPosition(itemKey);
            if (position >= 0) {
                notifyItemChanged(position);
            }
        }

        private String getSummaryKey(PaperItem item) {
            String link = item.getLink() == null ? "" : item.getLink().trim();
            if (!link.isEmpty()) {
                return link;
            }
            return (item.getTitle() == null ? "" : item.getTitle().trim()) + "|" + (item.getPublishedDate() == null ? "" : item.getPublishedDate().trim());
        }

        private int findItemPosition(String itemKey) {
            for (int index = 0; index < items.size(); index++) {
                if (itemKey.equals(getSummaryKey(items.get(index)))) {
                    return index;
                }
            }
            return -1;
        }

        private void toggleRecommendationHighlight(String itemKey, PaperItem item) {
            String primaryInterest = recommendationPrimaryInterests.get(itemKey);
            if (primaryInterest == null || primaryInterest.trim().isEmpty()) {
                return;
            }
            if (highlightedRecommendationKeys.contains(itemKey)) {
                highlightedRecommendationKeys.remove(itemKey);
            } else {
                highlightedRecommendationKeys.add(itemKey);
            }
            bindKeywordChips(item);
        }

        private void bindRecommendationReason(PaperItem item) {
            String reason = recommendationReasons.get(getSummaryKey(item));
            if (reason == null || reason.trim().isEmpty()) {
                binding.recommendationReasonLabel.setVisibility(View.GONE);
                binding.recommendationReasonText.setVisibility(View.GONE);
                return;
            }
            binding.recommendationReasonLabel.setVisibility(View.VISIBLE);
            binding.recommendationReasonText.setVisibility(View.VISIBLE);
            binding.recommendationReasonText.setText(reason.trim());
        }

        private void bindRecommendationScore(PaperItem item) {
            Integer score = recommendationScores.get(getSummaryKey(item));
            if (score == null) {
                binding.recommendationScoreLabel.setVisibility(View.GONE);
                binding.recommendationScoreText.setVisibility(View.GONE);
                return;
            }
            binding.recommendationScoreLabel.setVisibility(View.VISIBLE);
            binding.recommendationScoreText.setVisibility(View.VISIBLE);
            binding.recommendationScoreText.setText(String.valueOf(score));
        }

        private void bindBookmarkMetadata(PaperItem item) {
            if (enableBookmarking) {
                binding.bookmarkMetaText.setVisibility(View.GONE);
                binding.bookmarkTagsChipGroup.setVisibility(View.GONE);
                binding.bookmarkTagsChipGroup.removeAllViews();
                return;
            }
            Context context = binding.getRoot().getContext();
            BookmarkMetadataStore metadataStore = new BookmarkMetadataStore(context);
            String group = metadataStore.getGroup(item);
            String note = metadataStore.getNote(item);
            String tags = metadataStore.getTags(item);
            String metadataSummary = buildBookmarkMetadataSummary(context, group, note);
            if (metadataSummary.isEmpty()) {
                binding.bookmarkMetaText.setVisibility(View.GONE);
            } else {
                binding.bookmarkMetaText.setVisibility(View.VISIBLE);
                binding.bookmarkMetaText.setText(metadataSummary);
            }

            binding.bookmarkTagsChipGroup.removeAllViews();
            if (tags.isEmpty()) {
                binding.bookmarkTagsChipGroup.setVisibility(View.GONE);
            } else {
                binding.bookmarkTagsChipGroup.setVisibility(View.VISIBLE);
                List<String> splitTagList = splitTags(tags);
                int visibleCount = Math.min(splitTagList.size(), MAX_VISIBLE_BOOKMARK_TAGS);
                for (int index = 0; index < visibleCount; index++) {
                    String tag = splitTagList.get(index);
                    Chip chip = new Chip(context);
                    chip.setText(tag);
                    chip.setCheckable(false);
                    chip.setClickable(bookmarkTagClickListener != null);
                    styleChip(chip, context, 0.55f);
                    if (bookmarkTagClickListener != null) {
                        chip.setOnClickListener(v -> bookmarkTagClickListener.onBookmarkTagClicked(tag));
                    }
                    binding.bookmarkTagsChipGroup.addView(chip);
                }
                if (splitTagList.size() > MAX_VISIBLE_BOOKMARK_TAGS) {
                    Chip moreChip = new Chip(context);
                    moreChip.setText(context.getString(R.string.text_more_tags_count,
                            splitTagList.size() - MAX_VISIBLE_BOOKMARK_TAGS));
                    moreChip.setCheckable(false);
                    moreChip.setClickable(false);
                    styleChip(moreChip, context, 0.4f);
                    binding.bookmarkTagsChipGroup.addView(moreChip);
                }
            }
        }

        private String buildBookmarkMetadataSummary(Context context, String group, String note) {
            List<String> segments = new ArrayList<>();
            if (group != null && !group.trim().isEmpty()) {
                segments.add(context.getString(R.string.text_bookmark_meta_group, group.trim()));
            }
            if (note != null && !note.trim().isEmpty()) {
                segments.add(context.getString(R.string.text_bookmark_meta_note, note.trim()));
            }
            return TextUtils.join("  ·  ", segments);
        }

        private void editBookmarkMetadata(PaperItem item) {
            if (enableBookmarking) {
                return;
            }
            Context context = binding.getRoot().getContext();
            BookmarkMetadataStore metadataStore = new BookmarkMetadataStore(context);
            String currentGroup = metadataStore.getGroup(item);
            List<String> groupSuggestions = buildGroupSuggestions(currentGroup, metadataStore.getAllGroups());
            List<String> userTags = normalizeSuggestions(metadataStore.getAllTags());
            List<String> paperSuggestions = normalizeSuggestions(extractKeywords(item));

            ScrollView scrollView = new ScrollView(context);
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
            container.setPadding(padding, padding / 2, padding, 0);
            scrollView.addView(container);

            TextInputLayout groupLayout = new TextInputLayout(context);
            groupLayout.setHint(context.getString(R.string.label_bookmark_group));
            groupLayout.setHelperText(context.getString(R.string.message_group_suggestions));
            groupLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
            AutoCompleteTextView groupInput = new AutoCompleteTextView(context);
            groupInput.setThreshold(0);
            groupInput.setText(currentGroup, false);
            groupInput.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, groupSuggestions));
            groupInput.setOnClickListener(v -> groupInput.showDropDown());
            groupInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    groupInput.showDropDown();
                }
            });
            groupLayout.addView(groupInput);

            TextView groupSuggestionsLabel = createSuggestionLabel(context, R.string.title_suggested_groups);
            TextView groupSuggestionsHelper = createSuggestionHelper(context, R.string.message_groups_quick_pick);
            com.google.android.material.chip.ChipGroup groupSuggestionsGroup = createGroupSuggestionChipGroup(context, groupSuggestions, groupInput);

            TextInputLayout noteLayout = new TextInputLayout(context);
            noteLayout.setHint(context.getString(R.string.label_bookmark_note));
            TextInputEditText noteInput = new TextInputEditText(context);
            noteInput.setMinLines(3);
            noteInput.setText(metadataStore.getNote(item));
            noteLayout.addView(noteInput);

            TextInputLayout tagsLayout = new TextInputLayout(context);
            tagsLayout.setHint(context.getString(R.string.label_bookmark_tags));
            TextInputEditText tagsInput = new TextInputEditText(context);
            tagsLayout.setHelperText(context.getString(R.string.message_tag_custom_input));
            tagsInput.setText(metadataStore.getTags(item));
            tagsLayout.addView(tagsInput);

            TextView yourTagsLabel = createSuggestionLabel(context, R.string.title_your_tags);
            TextView yourTagsHelper = createSuggestionHelper(context, R.string.message_tags_from_bookmarks);
            com.google.android.material.chip.ChipGroup yourTagsGroup = createTagSuggestionChipGroup(context, userTags, tagsInput);

            TextView paperTagsLabel = createSuggestionLabel(context, R.string.title_suggested_from_paper);
            TextView paperTagsHelper = createSuggestionHelper(context, R.string.message_tags_from_paper);
            com.google.android.material.chip.ChipGroup paperTagsGroup = createTagSuggestionChipGroup(context, paperSuggestions, tagsInput);

            groupInput.addTextChangedListener(createAfterTextChangedWatcher(() ->
                    syncGroupSuggestionSelection(groupSuggestionsGroup, textOf(groupInput))
            ));
            tagsInput.addTextChangedListener(createAfterTextChangedWatcher(() -> {
                syncTagSuggestionSelection(yourTagsGroup, textOf(tagsInput));
                syncTagSuggestionSelection(paperTagsGroup, textOf(tagsInput));
            }));
            syncGroupSuggestionSelection(groupSuggestionsGroup, textOf(groupInput));
            syncTagSuggestionSelection(yourTagsGroup, textOf(tagsInput));
            syncTagSuggestionSelection(paperTagsGroup, textOf(tagsInput));

            container.addView(groupLayout);
            if (!groupSuggestions.isEmpty()) {
                container.addView(groupSuggestionsLabel);
                container.addView(groupSuggestionsHelper);
                container.addView(groupSuggestionsGroup);
            }
            container.addView(noteLayout);
            container.addView(tagsLayout);
            if (!userTags.isEmpty()) {
                container.addView(yourTagsLabel);
                container.addView(yourTagsHelper);
                container.addView(yourTagsGroup);
            }
            if (!paperSuggestions.isEmpty()) {
                container.addView(paperTagsLabel);
                container.addView(paperTagsHelper);
                container.addView(paperTagsGroup);
            }

            new AlertDialog.Builder(context)
                    .setTitle(R.string.action_edit_notes)
                    .setView(scrollView)
                    .setPositiveButton(R.string.action_save, (dialog, which) -> {
                        metadataStore.saveGroup(item, normalizeGroup(textOf(groupInput)));
                        metadataStore.saveNote(item, textOf(noteInput));
                        metadataStore.saveTags(item, normalizeTags(textOf(tagsInput)));
                        bindBookmarkMetadata(item);
                        if (bookmarkMetadataChangedListener != null) {
                            bookmarkMetadataChangedListener.onBookmarkMetadataChanged();
                        }
                        Snackbar.make(binding.getRoot(), R.string.message_bookmark_metadata_saved, Snackbar.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private String textOf(TextInputEditText editText) {
            return editText.getText() == null ? "" : editText.getText().toString().trim();
        }

        private String textOf(AutoCompleteTextView input) {
            return input.getText() == null ? "" : input.getText().toString().trim();
        }

        private TextView createSuggestionLabel(Context context, int textRes) {
            TextView label = new TextView(context);
            label.setText(textRes);
            label.setTypeface(label.getTypeface(), Typeface.BOLD);
            int marginTop = dp(context, 12);
            label.setPadding(0, marginTop, 0, 0);
            return label;
        }

        private TextView createSuggestionHelper(Context context, int textRes) {
            TextView helper = new TextView(context);
            helper.setText(textRes);
            helper.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            int marginBottom = dp(context, 4);
            helper.setPadding(0, 0, 0, marginBottom);
            return helper;
        }

        private com.google.android.material.chip.ChipGroup createGroupSuggestionChipGroup(
                Context context,
                List<String> suggestions,
                AutoCompleteTextView groupInput
        ) {
            com.google.android.material.chip.ChipGroup chipGroup = new com.google.android.material.chip.ChipGroup(context);
            chipGroup.setChipSpacingHorizontal(dp(context, 8));
            chipGroup.setChipSpacingVertical(dp(context, 8));
            for (String suggestion : suggestions) {
                if (suggestion == null || suggestion.trim().isEmpty()) {
                    continue;
                }
                String normalizedSuggestion = normalizeGroup(suggestion);
                Chip chip = new Chip(context);
                chip.setText(normalizedSuggestion);
                chip.setCheckable(true);
                chip.setClickable(true);
                chip.setEnsureMinTouchTargetSize(false);
                styleChip(chip, context, 0.8f);
                chip.setOnClickListener(v -> {
                    groupInput.setText(normalizedSuggestion, false);
                    groupInput.setSelection(groupInput.length());
                    syncGroupSuggestionSelection(chipGroup, normalizedSuggestion);
                });
                chipGroup.addView(chip);
            }
            return chipGroup;
        }

        private com.google.android.material.chip.ChipGroup createTagSuggestionChipGroup(
                Context context,
                List<String> suggestions,
                TextInputEditText tagsInput
        ) {
            com.google.android.material.chip.ChipGroup chipGroup = new com.google.android.material.chip.ChipGroup(context);
            chipGroup.setChipSpacingHorizontal(dp(context, 8));
            chipGroup.setChipSpacingVertical(dp(context, 8));
            for (String suggestion : suggestions) {
                if (suggestion == null || suggestion.trim().isEmpty()) {
                    continue;
                }
                Chip chip = new Chip(context);
                chip.setText(suggestion.trim());
                chip.setCheckable(true);
                chip.setClickable(true);
                chip.setEnsureMinTouchTargetSize(false);
                styleChip(chip, context, 0.7f);
                chip.setOnClickListener(v -> toggleTagSuggestion(tagsInput, suggestion.trim()));
                chipGroup.addView(chip);
            }
            return chipGroup;
        }

        private void toggleTagSuggestion(TextInputEditText tagsInput, String suggestion) {
            String normalizedSuggestion = normalizeTag(suggestion);
            if (normalizedSuggestion.isEmpty()) {
                return;
            }
            List<String> tags = new ArrayList<>(splitTags(textOf(tagsInput)));
            int existingIndex = indexOfIgnoreCase(tags, normalizedSuggestion);
            if (existingIndex >= 0) {
                tags.remove(existingIndex);
            } else {
                tags.add(normalizedSuggestion);
            }
            tagsInput.setText(String.join(", ", tags));
            Editable editable = tagsInput.getText();
            tagsInput.setSelection(editable == null ? 0 : editable.length());
        }

        private void syncGroupSuggestionSelection(com.google.android.material.chip.ChipGroup chipGroup, String currentValue) {
            String normalizedCurrent = normalizeGroup(currentValue);
            for (int index = 0; index < chipGroup.getChildCount(); index++) {
                View child = chipGroup.getChildAt(index);
                if (!(child instanceof Chip)) {
                    continue;
                }
                Chip chip = (Chip) child;
                chip.setChecked(chip.getText().toString().equalsIgnoreCase(normalizedCurrent));
            }
        }

        private void syncTagSuggestionSelection(com.google.android.material.chip.ChipGroup chipGroup, String rawTags) {
            List<String> selectedTags = splitTags(rawTags);
            for (int index = 0; index < chipGroup.getChildCount(); index++) {
                View child = chipGroup.getChildAt(index);
                if (!(child instanceof Chip)) {
                    continue;
                }
                Chip chip = (Chip) child;
                chip.setChecked(indexOfIgnoreCase(selectedTags, chip.getText().toString()) >= 0);
            }
        }

        private int dp(Context context, int dp) {
            return (int) (dp * context.getResources().getDisplayMetrics().density);
        }

        private TextWatcher createAfterTextChangedWatcher(Runnable action) {
            return new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    action.run();
                }
            };
        }

        private List<String> buildGroupSuggestions(String currentGroup, List<String> storedGroups) {
            List<String> suggestions = new ArrayList<>();
            if (!normalizeGroup(currentGroup).isEmpty()) {
                suggestions.add(normalizeGroup(currentGroup));
            }
            if (storedGroups != null) {
                suggestions.addAll(storedGroups);
            }
            suggestions.addAll(DEFAULT_BOOKMARK_GROUPS);
            return normalizeSuggestions(suggestions);
        }

        private List<String> normalizeSuggestions(List<String> rawSuggestions) {
            List<String> normalized = new ArrayList<>();
            if (rawSuggestions == null) {
                return normalized;
            }
            for (String rawSuggestion : rawSuggestions) {
                String suggestion = normalizeTag(rawSuggestion);
                if (suggestion.isEmpty() || indexOfIgnoreCase(normalized, suggestion) >= 0) {
                    continue;
                }
                normalized.add(suggestion);
            }
            return normalized;
        }

        private String normalizeGroup(String value) {
            return value == null ? "" : value.trim().replaceAll("\\s{2,}", " ");
        }

        private String normalizeTag(String value) {
            return value == null ? "" : value.trim();
        }

        private int indexOfIgnoreCase(List<String> values, String candidate) {
            if (values == null || candidate == null) {
                return -1;
            }
            for (int index = 0; index < values.size(); index++) {
                if (candidate.equalsIgnoreCase(values.get(index))) {
                    return index;
                }
            }
            return -1;
        }

        private String normalizeTags(String rawTags) {
            if (rawTags.isEmpty()) {
                return "";
            }
            String[] parts = rawTags.split(",");
            List<String> normalized = new ArrayList<>();
            for (String part : parts) {
                String trimmed = normalizeTag(part);
                if (!trimmed.isEmpty() && indexOfIgnoreCase(normalized, trimmed) < 0) {
                    normalized.add(trimmed);
                }
            }
            return String.join(", ", normalized);
        }

        private List<String> splitTags(String rawTags) {
            if (rawTags == null || rawTags.trim().isEmpty()) {
                return Collections.emptyList();
            }
            String[] parts = rawTags.split(",");
            List<String> tags = new ArrayList<>();
            for (String part : parts) {
                String trimmed = normalizeTag(part);
                if (!trimmed.isEmpty() && indexOfIgnoreCase(tags, trimmed) < 0) {
                    tags.add(trimmed);
                }
            }
            return tags;
        }

        private void bindKeywordChips(PaperItem item) {
            binding.keywordChipGroup.removeAllViews();
            List<String> keywords = extractKeywords(item);
            String itemKey = getSummaryKey(item);
            String highlightedInterest = recommendationPrimaryInterests.get(itemKey);
            boolean shouldHighlight = highlightedRecommendationKeys.contains(itemKey)
                    && highlightedInterest != null
                    && !highlightedInterest.trim().isEmpty();
            boolean shouldPinKeyword = !pinnedKeyword.isEmpty();
            boolean hasHighlightChip = false;
            boolean hasPinnedChip = false;
            if (keywords.isEmpty() && !shouldHighlight && !shouldPinKeyword) {
                binding.keywordChipGroup.setVisibility(View.GONE);
                return;
            }
            binding.keywordChipGroup.setVisibility(View.VISIBLE);
            Context context = binding.getRoot().getContext();
            for (String keyword : keywords) {
                Chip chip = new Chip(context);
                chip.setText(keyword);
                boolean isHighlighted = shouldHighlight && isMatchingInterest(keyword, highlightedInterest);
                boolean isPinned = shouldPinKeyword && isMatchingInterest(keyword, pinnedKeyword);
                chip.setCheckable(isHighlighted);
                chip.setChecked(isHighlighted);
                chip.setClickable(keywordClickListener != null);
                styleChip(chip, context, 0.55f);
                if (keywordClickListener != null) {
                    chip.setOnClickListener(v -> keywordClickListener.onKeywordClicked(keyword));
                }
                if (isHighlighted) {
                    hasHighlightChip = true;
                }
                if (isPinned) {
                    hasPinnedChip = true;
                }
                binding.keywordChipGroup.addView(chip);
            }
            if (shouldHighlight && !hasHighlightChip) {
                Chip chip = new Chip(context);
                chip.setText(highlightedInterest);
                chip.setCheckable(true);
                chip.setChecked(true);
                chip.setClickable(false);
                styleChip(chip, context, 0.55f);
                binding.keywordChipGroup.addView(chip);
            }
            if (shouldPinKeyword && !hasPinnedChip) {
                Chip chip = new Chip(context);
                chip.setText(pinnedKeyword);
                chip.setCheckable(true);
                chip.setChecked(true);
                chip.setClickable(keywordClickListener != null);
                styleChip(chip, context, 0.55f);
                if (keywordClickListener != null) {
                    chip.setOnClickListener(v -> keywordClickListener.onKeywordClicked(pinnedKeyword));
                }
                binding.keywordChipGroup.addView(chip, 0);
            }
        }

        private void styleChip(Chip chip, Context context, float maxWidthRatio) {
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setMaxWidth((int) (context.getResources().getDisplayMetrics().widthPixels * maxWidthRatio));
        }

        private List<String> extractKeywords(PaperItem item) {
            Map<String, Integer> scores = new LinkedHashMap<>();
            addKeywordScores(scores, item.getTitle(), 3);
            addKeywordScores(scores, item.getSummary(), 1);
            if (scores.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(scores.entrySet());
            sortedEntries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                    .thenComparing(Map.Entry::getKey));
            List<String> keywords = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : sortedEntries) {
                keywords.add(formatKeyword(entry.getKey()));
                if (keywords.size() == MAX_VISIBLE_KEYWORDS) {
                    break;
                }
            }
            return keywords;
        }

        private void addKeywordScores(Map<String, Integer> scores, String text, int weight) {
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            String normalizedText = text.toLowerCase(Locale.US).replaceAll("[^a-z0-9#+-]", " ");
            String[] words = normalizedText.split("\\s+");
            for (String word : words) {
                if (!isKeywordCandidate(word)) {
                    continue;
                }
                scores.put(word, scores.getOrDefault(word, 0) + weight);
            }
        }

        private boolean isKeywordCandidate(String word) {
            if (word == null || word.length() < 3) {
                return false;
            }
            if (KEYWORD_STOP_WORDS.contains(word)) {
                return false;
            }
            boolean hasLetter = false;
            for (int index = 0; index < word.length(); index++) {
                if (Character.isLetter(word.charAt(index))) {
                    hasLetter = true;
                    break;
                }
            }
            return hasLetter;
        }

        private String formatKeyword(String keyword) {
            if (keyword.isEmpty()) {
                return keyword;
            }
            if (keyword.length() <= 4 && keyword.equals(keyword.toUpperCase(Locale.US))) {
                return keyword;
            }
            return Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1);
        }

        private boolean isMatchingInterest(String keyword, String interest) {
            String normalizedKeyword = normalizeForCompare(keyword);
            String normalizedInterest = normalizeForCompare(interest);
            return !normalizedKeyword.isEmpty()
                    && !normalizedInterest.isEmpty()
                    && (normalizedKeyword.contains(normalizedInterest) || normalizedInterest.contains(normalizedKeyword));
        }

        private String normalizeForCompare(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.US);
        }
    }

    public interface OnKeywordClickListener {
        void onKeywordClicked(String keyword);
    }

    public interface OnBookmarkMetadataChangedListener {
        void onBookmarkMetadataChanged();
    }

    public interface OnBookmarkTagClickListener {
        void onBookmarkTagClicked(String tag);
    }

    public interface OnBookmarkDeleteRequestListener {
        void onBookmarkDeleteRequested(PaperItem item);
    }
}
