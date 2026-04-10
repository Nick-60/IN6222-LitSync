
package com.in6222.litsync.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.room.Room;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.in6222.litsync.R;
import com.in6222.litsync.ai.AiAssistant;
import com.in6222.litsync.database.AppDatabase;
import com.in6222.litsync.databinding.FragmentSearchBinding;
import com.in6222.litsync.databinding.LayoutAiInsightSheetBinding;
import com.in6222.litsync.model.ArxivFeed;
import com.in6222.litsync.model.PaperItem;
import com.in6222.litsync.network.RetrofitClient;
import com.in6222.litsync.repository.PaperRepository;
import com.in6222.litsync.util.PaperResultsCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchFragment extends BaseFragment {

    private static final String SEARCH_PREFS = "search_filters";
    private static final String KEY_SUBJECT = "subject";
    private static final String KEY_SORT = "sort";
    private static final String KEY_RESULT_COUNT = "result_count";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_FILTERS_EXPANDED = "filters_expanded";
    private static final String KEY_HEADER_COLLAPSED = "header_collapsed";
    private static final String SUBJECT_ALL = "all";
    private static final String SUBJECT_CS = "cs";
    private static final String SUBJECT_MATH = "math";
    private static final String SUBJECT_PHYSICS = "physics";
    private static final String SUBJECT_STATISTICS = "statistics";
    private static final String SUBJECT_EESS = "eess";
    private static final String SUBJECT_QBIO = "qbio";
    private static final String SUBJECT_ECON = "econ";
    private static final String SORT_RELEVANCE = "relevance";
    private static final String SORT_LATEST = "latest";
    private static final String HISTORY_SEPARATOR = "\u001F";
    private static final int MAX_HISTORY_SIZE = 8;
    private static final int COLLAPSED_HISTORY_COUNT = 2;
    private static final int PAGINATION_ELLIPSIS = -1;

    private FragmentSearchBinding binding;
    private ExecutorService executor;
    private PaperRepository repository;
    private PaperAdapter adapter;
    private AiAssistant aiAssistant;
    private PaperResultsCache resultsCache;
    private SharedPreferences preferences;
    private BottomSheetDialog aiInsightDialog;
    private LayoutAiInsightSheetBinding aiInsightSheetBinding;

    private final List<PaperItem> searchResults = new ArrayList<>();
    private final List<String> searchHistory = new ArrayList<>();
    private final List<Integer> resultCountOptions = Arrays.asList(10, 20, 50);

    private String lastSearchQuery = "";
    private String lastAiInsight = "";
    private String selectedSubject = SUBJECT_ALL;
    private String selectedSort = SORT_RELEVANCE;
    private int selectedResultCount = 20;
    private boolean filtersExpanded = false;
    private boolean headerCollapsed = false;
    private int currentPage = 1;
    private int totalPages = 0;
    private int totalResults = 0;
    private boolean isPageLoading = false;
    private boolean isBlockingLoading = false;
    private boolean historyExpanded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        executor = Executors.newSingleThreadExecutor();
        AppDatabase database = Room.databaseBuilder(requireContext().getApplicationContext(), AppDatabase.class, "litsync.db").build();
        repository = new PaperRepository(RetrofitClient.getService(), database);
        aiAssistant = AiAssistant.create(requireContext());
        resultsCache = new PaperResultsCache(requireContext());
        preferences = requireContext().getSharedPreferences(SEARCH_PREFS, Context.MODE_PRIVATE);
        adapter = new PaperAdapter(repository, executor, true, aiAssistant);
        adapter.setOnKeywordClickListener(this::searchByKeyword);

        loadSavedState();
        setupFilterMenus();

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.searchButton.setOnClickListener(v -> submitSearchQuery());
        binding.applyFiltersButton.setOnClickListener(v -> applyAdvancedFilters());
        binding.resetFiltersButton.setOnClickListener(v -> resetFilters());
        binding.toggleFiltersButton.setOnClickListener(v -> toggleFilters());
        binding.filterHeader.setOnClickListener(v -> toggleFilters());
        binding.clearHistoryButton.setOnClickListener(v -> clearSearchHistory());
        binding.historyToggleButton.setOnClickListener(v -> toggleSearchHistory());
        binding.firstPageButton.setOnClickListener(v -> goToSearchPage(1));
        binding.previousPageButton.setOnClickListener(v -> goToSearchPage(currentPage - 1));
        binding.nextPageButton.setOnClickListener(v -> goToSearchPage(currentPage + 1));
        binding.collapseHeaderButton.setOnClickListener(v -> setHeaderCollapsed(true));
        binding.expandHeaderButton.setOnClickListener(v -> setHeaderCollapsed(false));

        binding.aiRewriteButton.setOnClickListener(v -> rewriteSearchQuery());
        binding.aiAnalyzeButton.setOnClickListener(v -> openAiInsights());

        binding.searchInput.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            boolean keyboardSearch = actionId == EditorInfo.IME_ACTION_SEARCH;
            boolean enterPressed = event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (keyboardSearch || enterPressed) {
                submitSearchQuery();
                return true;
            }
            return false;
        });

        hideAiInsight();
        refreshSearchHistoryUi();
        applyFilterExpansionState();
        applyHeaderCollapseState();
        updatePaginationUi();
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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) {
            executor.shutdown();
        }
        dismissAiInsightDialog();
        binding = null;
    }

    private void submitSearchQuery() {
        if (binding == null) {
            return;
        }
        String query = textOf(binding.searchInput.getText());
        if (query.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.message_enter_search_keyword, Snackbar.LENGTH_SHORT).show();
            return;
        }
        loadSearch(query);
    }

    private void applyAdvancedFilters() {
        if (binding == null) {
            return;
        }
        String query = textOf(binding.searchInput.getText());
        if (query.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.message_enter_search_keyword, Snackbar.LENGTH_SHORT).show();
            return;
        }
        loadSearch(query);
    }

    private void loadSearch(String query) {
        requestSearchPage(query, 1, true, true);
    }

    private void goToSearchPage(int page) {
        if (binding == null || isPageLoading || totalPages == 0 || lastSearchQuery.trim().isEmpty()) {
            return;
        }
        int targetPage = Math.max(1, Math.min(page, totalPages));
        if (targetPage == currentPage) {
            return;
        }
        requestSearchPage(lastSearchQuery, targetPage, false, false);
    }

    private void requestSearchPage(String query, int page, boolean resetResults, boolean addToHistory) {
        if (binding == null || isPageLoading) {
            return;
        }
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return;
        }
        int targetPage = Math.max(1, page);
        if (!resetResults && totalPages > 0) {
            targetPage = Math.min(targetPage, totalPages);
        }
        final int requestedPage = targetPage;
        lastSearchQuery = normalizedQuery;
        if (addToHistory) {
            addSearchHistory(normalizedQuery);
        }
        hideAiInsight();
        isPageLoading = true;
        isBlockingLoading = resetResults;
        if (resetResults) {
            currentPage = 1;
            totalPages = 0;
            totalResults = 0;
            searchResults.clear();
            updatePaginationUi();
            setHeaderCollapsed(true);
            collapseFiltersIfExpanded();
            showLoading(binding);
            binding.statusText.setText(R.string.status_search_loading);
        } else {
            updatePaginationUi();
        }
        String searchQuery = buildSearchQuery(normalizedQuery);
        Call<ArxivFeed> call = repository.searchPapers(
                searchQuery,
                (requestedPage - 1) * selectedResultCount,
                selectedResultCount,
                getSortByValue(),
                getSortOrderValue()
        );
        call.enqueue(new Callback<ArxivFeed>() {
            @Override
            public void onResponse(@NonNull Call<ArxivFeed> call, @NonNull Response<ArxivFeed> response) {
                if (binding == null) {
                    return;
                }
                isPageLoading = false;
                isBlockingLoading = false;
                if (!response.isSuccessful()) {
                    updatePaginationUi();
                    String errorMessage = formatNetworkResponseError(response);
                    if (resetResults) {
                        showError(binding, errorMessage);
                    } else {
                        Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
                    }
                    return;
                }
                ArxivFeed feed = response.body();
                if (feed == null || feed.getEntries() == null || feed.getEntries().isEmpty()) {
                    if (resetResults) {
                        totalResults = 0;
                        totalPages = 0;
                        currentPage = 1;
                        updatePaginationUi();
                        showEmpty(binding, getString(R.string.status_empty_search_guidance));
                    } else {
                        updatePaginationUi();
                        Snackbar.make(binding.getRoot(), R.string.status_no_more_results, Snackbar.LENGTH_SHORT).show();
                    }
                    return;
                }
                List<PaperItem> items = mapEntries(feed.getEntries());
                currentPage = requestedPage;
                totalResults = resolveTotalResults(feed, items.size(), requestedPage);
                totalPages = calculateTotalPages(totalResults);
                searchResults.clear();
                searchResults.addAll(items);
                adapter.setItems(items);
                cacheSearchResults(normalizedQuery, requestedPage, items);
                updatePaginationUi();
                showContent(binding);
                scrollResultsToTop();
            }

            @Override
            public void onFailure(@NonNull Call<ArxivFeed> call, @NonNull Throwable t) {
                if (binding == null) {
                    return;
                }
                isPageLoading = false;
                isBlockingLoading = false;
                updatePaginationUi();
                if (restoreCachedSearchResults(normalizedQuery, requestedPage)) {
                    return;
                }
                String errorMessage = formatNetworkFailure(t);
                if (resetResults) {
                    showError(binding, errorMessage);
                } else {
                    Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void rewriteSearchQuery() {
        if (binding == null) {
            return;
        }
        String query = textOf(binding.searchInput.getText());
        if (query.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.message_enter_search_keyword, Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (aiAssistant == null || !aiAssistant.isAvailable()) {
            Snackbar.make(binding.getRoot(), R.string.message_ai_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }
        binding.aiRewriteButton.setEnabled(false);
        executor.execute(() -> {
            try {
                String rewritten = aiAssistant.rewriteSearchQuery(query);
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    binding.aiRewriteButton.setEnabled(true);
                    if (rewritten.trim().isEmpty()) {
                        Snackbar.make(binding.getRoot(), R.string.message_ai_empty_response, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    binding.searchInput.setText(rewritten);
                    binding.searchInput.setSelection(rewritten.length());
                    Snackbar.make(binding.getRoot(), R.string.message_ai_query_updated, Snackbar.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (binding != null) {
                        binding.aiRewriteButton.setEnabled(true);
                        Snackbar.make(binding.getRoot(), safeMessage(e, R.string.message_ai_request_failed), Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void openAiInsights() {
        if (binding == null) {
            return;
        }
        if (!lastAiInsight.trim().isEmpty()) {
            showAiInsight(lastAiInsight);
            return;
        }
        analyzeSearchResults(false);
    }

    private void analyzeSearchResults(boolean forceRefresh) {
        if (binding == null) {
            return;
        }
        if (searchResults.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.message_ai_results_require_papers, Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (aiAssistant == null || !aiAssistant.isAvailable()) {
            Snackbar.make(binding.getRoot(), R.string.message_ai_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }
        binding.aiAnalyzeButton.setEnabled(false);
        showAiInsightLoading();

        List<PaperItem> snapshot = new ArrayList<>(searchResults);
        executor.execute(() -> {
            try {
                String insight = forceRefresh
                        ? aiAssistant.regenerateSearchResultsSummary(lastSearchQuery, snapshot)
                        : aiAssistant.summarizeSearchResults(lastSearchQuery, snapshot);
                runOnUiThread(() -> showAiInsight(insight));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideAiInsight();
                    if (binding != null) {
                        Snackbar.make(binding.getRoot(), safeMessage(e, R.string.message_ai_request_failed), Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showAiInsight(String insight) {
        if (binding == null) {
            return;
        }
        binding.aiAnalyzeButton.setEnabled(true);
        lastAiInsight = insight == null ? "" : insight;
        ensureAiInsightDialog();
        if (aiInsightSheetBinding == null || aiInsightDialog == null) {
            return;
        }
        aiInsightSheetBinding.aiInsightSheetProgress.setVisibility(View.GONE);
        aiInsightSheetBinding.aiInsightDivider.setVisibility(View.VISIBLE);
        aiInsightSheetBinding.aiInsightSheetActions.setVisibility(View.VISIBLE);
        aiInsightSheetBinding.aiInsightSheetText.setText(lastAiInsight);
        aiInsightDialog.show();
    }

    private void hideAiInsight() {
        if (binding != null) {
            binding.aiAnalyzeButton.setEnabled(true);
        }
        lastAiInsight = "";
        if (aiInsightSheetBinding != null) {
            aiInsightSheetBinding.aiInsightSheetProgress.setVisibility(View.GONE);
            aiInsightSheetBinding.aiInsightDivider.setVisibility(View.GONE);
            aiInsightSheetBinding.aiInsightSheetActions.setVisibility(View.GONE);
            aiInsightSheetBinding.aiInsightSheetText.setText("");
        }
        if (aiInsightDialog != null && aiInsightDialog.isShowing()) {
            aiInsightDialog.dismiss();
        }
    }

    private void copyAiInsight() {
        if (binding == null || lastAiInsight.trim().isEmpty()) {
            return;
        }
        ClipboardManager clipboardManager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            Snackbar.make(binding.getRoot(), R.string.message_copy_failed, Snackbar.LENGTH_SHORT).show();
            return;
        }
        clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.title_ai_results), lastAiInsight));
        Snackbar.make(binding.getRoot(), R.string.message_copied, Snackbar.LENGTH_SHORT).show();
    }

    private void shareAiInsight() {
        if (binding == null || lastAiInsight.trim().isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, lastAiInsight);
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
    }

    private void searchByKeyword(String keyword) {
        if (binding == null) {
            return;
        }
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            return;
        }
        binding.searchInput.setText(normalized);
        binding.searchInput.setSelection(normalized.length());
        loadSearch(normalized);
    }

    private void setupFilterMenus() {
        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line,
                new String[]{
                        getString(R.string.option_subject_all),
                        getString(R.string.option_subject_cs),
                        getString(R.string.option_subject_math),
                        getString(R.string.option_subject_physics),
                        getString(R.string.option_subject_statistics),
                        getString(R.string.option_subject_eess),
                        getString(R.string.option_subject_qbio),
                        getString(R.string.option_subject_econ)
                });
        binding.subjectFilterInput.setAdapter(subjectAdapter);
        binding.subjectFilterInput.setText(getSubjectLabel(selectedSubject), false);
        binding.subjectFilterInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedSubject = subjectKeyForPosition(position);
            saveState();
            updateCollapsedHeaderSummary();
        });

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line,
                new String[]{getString(R.string.option_sort_relevance), getString(R.string.option_sort_latest)});
        binding.sortFilterInput.setAdapter(sortAdapter);
        binding.sortFilterInput.setText(getSortLabel(selectedSort), false);
        binding.sortFilterInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedSort = position == 1 ? SORT_LATEST : SORT_RELEVANCE;
            saveState();
            updateCollapsedHeaderSummary();
        });

        ArrayAdapter<String> countAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, new String[]{"10", "20", "50"});
        binding.resultCountInput.setAdapter(countAdapter);
        binding.resultCountInput.setText(String.valueOf(selectedResultCount), false);
        binding.resultCountInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedResultCount = resultCountOptions.get(position);
            saveState();
            updateCollapsedHeaderSummary();
        });
    }

    private void toggleFilters() {
        filtersExpanded = !filtersExpanded;
        applyFilterExpansionState();
        saveState();
    }

    private void applyFilterExpansionState() {
        binding.filterContent.setVisibility(filtersExpanded ? View.VISIBLE : View.GONE);
        binding.toggleFiltersButton.setText(filtersExpanded
                ? R.string.action_hide_advanced_search_short
                : R.string.action_show_advanced_search_short);
    }

    private void collapseFiltersIfExpanded() {
        if (!filtersExpanded) {
            return;
        }
        filtersExpanded = false;
        applyFilterExpansionState();
        saveState();
    }

    private void resetFilters() {
        selectedSubject = SUBJECT_ALL;
        selectedSort = SORT_RELEVANCE;
        selectedResultCount = 20;
        binding.subjectFilterInput.setText(getSubjectLabel(selectedSubject), false);
        binding.sortFilterInput.setText(getSortLabel(selectedSort), false);
        binding.resultCountInput.setText(String.valueOf(selectedResultCount), false);
        saveState();
        updateCollapsedHeaderSummary();
        Snackbar.make(binding.getRoot(), R.string.message_filters_reset, Snackbar.LENGTH_SHORT).show();
    }

    private void setHeaderCollapsed(boolean collapsed) {
        headerCollapsed = collapsed;
        applyHeaderCollapseState();
        saveState();
    }

    private void applyHeaderCollapseState() {
        binding.headerExpandedContainer.setVisibility(headerCollapsed ? View.GONE : View.VISIBLE);
        binding.headerCollapsedContainer.setVisibility(headerCollapsed ? View.VISIBLE : View.GONE);
        updateCollapsedHeaderSummary();
        updatePaginationUi();
    }

    private void updateCollapsedHeaderSummary() {
        String query = lastSearchQuery.trim();
        if (query.isEmpty()) {
            query = textOf(binding.searchInput.getText());
        }
        String summary = getString(R.string.text_search_header_summary,
                query.isEmpty() ? getString(R.string.value_none) : query,
                getSubjectLabel(selectedSubject),
                getSortLabel(selectedSort),
                String.valueOf(selectedResultCount));
        binding.collapsedHeaderSummary.setText(summary);
    }

    private void updatePaginationUi() {
        boolean hasMultiplePages = totalPages > 1;
        boolean show = headerCollapsed && !isBlockingLoading && !searchResults.isEmpty() && (hasMultiplePages || isPageLoading);
        binding.paginationContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.paginationProgress.setVisibility(isPageLoading ? View.VISIBLE : View.GONE);
        binding.paginationSummaryText.setText(getString(
                R.string.text_pagination_summary,
                Math.max(currentPage, 1),
                Math.max(totalPages, 1)
        ));
        binding.firstPageButton.setEnabled(!isPageLoading && currentPage > 1);
        binding.previousPageButton.setEnabled(!isPageLoading && currentPage > 1);
        binding.nextPageButton.setEnabled(!isPageLoading && currentPage < totalPages);
        binding.pageChipContainer.removeAllViews();
        if (show && totalPages > 0) {
            for (int page : buildVisiblePages()) {
                if (page == PAGINATION_ELLIPSIS) {
                    TextView textView = new TextView(requireContext());
                    int horizontalPadding = dp(4);
                    textView.setPadding(horizontalPadding, 0, horizontalPadding, 0);
                    textView.setText("…");
                    binding.pageChipContainer.addView(textView);
                    continue;
                }
                binding.pageChipContainer.addView(createPageChip(page));
            }
        }
    }

    private void addSearchHistory(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return;
        }
        searchHistory.remove(normalized);
        searchHistory.add(0, normalized);
        while (searchHistory.size() > MAX_HISTORY_SIZE) {
            searchHistory.remove(searchHistory.size() - 1);
        }
        historyExpanded = false;
        saveHistory();
        refreshSearchHistoryUi();
    }

    private void clearSearchHistory() {
        searchHistory.clear();
        historyExpanded = false;
        saveHistory();
        refreshSearchHistoryUi();
        Snackbar.make(binding.getRoot(), R.string.message_history_cleared, Snackbar.LENGTH_SHORT).show();
    }

    private void toggleSearchHistory() {
        historyExpanded = !historyExpanded;
        refreshSearchHistoryUi();
    }

    private void refreshSearchHistoryUi() {
        binding.searchHistoryGroup.removeAllViews();
        binding.searchHistoryCard.setVisibility(searchHistory.isEmpty() ? View.GONE : View.VISIBLE);
        int visibleCount = historyExpanded ? searchHistory.size() : Math.min(searchHistory.size(), COLLAPSED_HISTORY_COUNT);
        for (int i = 0; i < visibleCount; i++) {
            String query = searchHistory.get(i);
            Chip chip = new Chip(requireContext());
            chip.setText(query);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setOnClickListener(v -> {
                binding.searchInput.setText(query);
                binding.searchInput.setSelection(query.length());
                loadSearch(query);
            });
            binding.searchHistoryGroup.addView(chip);
        }
        int hiddenCount = Math.max(0, searchHistory.size() - visibleCount);
        if (hiddenCount > 0 || historyExpanded) {
            binding.historyToggleButton.setVisibility(View.VISIBLE);
            if (historyExpanded) {
                binding.historyToggleButton.setText(R.string.action_show_less);
            } else {
                binding.historyToggleButton.setText(getString(R.string.text_recent_searches_more, hiddenCount));
            }
        } else {
            binding.historyToggleButton.setVisibility(View.GONE);
        }
    }

    private void loadSavedState() {
        selectedSubject = preferences.getString(KEY_SUBJECT, SUBJECT_ALL);
        selectedSort = preferences.getString(KEY_SORT, SORT_RELEVANCE);
        selectedResultCount = preferences.getInt(KEY_RESULT_COUNT, 20);
        filtersExpanded = preferences.getBoolean(KEY_FILTERS_EXPANDED, false);
        headerCollapsed = preferences.getBoolean(KEY_HEADER_COLLAPSED, false);
        String rawHistory = preferences.getString(KEY_HISTORY, "");
        if (rawHistory != null && !rawHistory.trim().isEmpty()) {
            String[] values = rawHistory.split(HISTORY_SEPARATOR);
            for (String value : values) {
                String item = value == null ? "" : value.trim();
                if (!item.isEmpty() && !searchHistory.contains(item)) {
                    searchHistory.add(item);
                }
            }
        }
    }

    private void saveState() {
        preferences.edit()
                .putString(KEY_SUBJECT, selectedSubject)
                .putString(KEY_SORT, selectedSort)
                .putInt(KEY_RESULT_COUNT, selectedResultCount)
                .putBoolean(KEY_FILTERS_EXPANDED, filtersExpanded)
                .putBoolean(KEY_HEADER_COLLAPSED, headerCollapsed)
                .apply();
    }

    private void saveHistory() {
        String joined = String.join(HISTORY_SEPARATOR, searchHistory);
        preferences.edit().putString(KEY_HISTORY, joined).apply();
    }

    private String buildSearchQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (SUBJECT_CS.equals(selectedSubject)) {
            return "cat:cs.* AND all:" + query;
        }
        if (SUBJECT_MATH.equals(selectedSubject)) {
            return "cat:math.* AND all:" + query;
        }
        if (SUBJECT_PHYSICS.equals(selectedSubject)) {
            return "cat:physics.* AND all:" + query;
        }
        if (SUBJECT_STATISTICS.equals(selectedSubject)) {
            return "cat:stat.* AND all:" + query;
        }
        if (SUBJECT_EESS.equals(selectedSubject)) {
            return "cat:eess.* AND all:" + query;
        }
        if (SUBJECT_QBIO.equals(selectedSubject)) {
            return "cat:q-bio.* AND all:" + query;
        }
        if (SUBJECT_ECON.equals(selectedSubject)) {
            return "cat:econ.* AND all:" + query;
        }
        return "all:" + query;
    }

    private String getSortByValue() {
        return SORT_LATEST.equals(selectedSort) ? "submittedDate" : "relevance";
    }

    private String getSortOrderValue() {
        return "descending";
    }

    private String getSubjectLabel(String subjectKey) {
        if (SUBJECT_CS.equals(subjectKey)) return getString(R.string.option_subject_cs);
        if (SUBJECT_MATH.equals(subjectKey)) return getString(R.string.option_subject_math);
        if (SUBJECT_PHYSICS.equals(subjectKey)) return getString(R.string.option_subject_physics);
        if (SUBJECT_STATISTICS.equals(subjectKey)) return getString(R.string.option_subject_statistics);
        if (SUBJECT_EESS.equals(subjectKey)) return getString(R.string.option_subject_eess);
        if (SUBJECT_QBIO.equals(subjectKey)) return getString(R.string.option_subject_qbio);
        if (SUBJECT_ECON.equals(subjectKey)) return getString(R.string.option_subject_econ);
        return getString(R.string.option_subject_all);
    }

    private String getSortLabel(String sortKey) {
        return SORT_LATEST.equals(sortKey) ? getString(R.string.option_sort_latest) : getString(R.string.option_sort_relevance);
    }

    private String subjectKeyForPosition(int position) {
        if (position == 1) return SUBJECT_CS;
        if (position == 2) return SUBJECT_MATH;
        if (position == 3) return SUBJECT_PHYSICS;
        if (position == 4) return SUBJECT_STATISTICS;
        if (position == 5) return SUBJECT_EESS;
        if (position == 6) return SUBJECT_QBIO;
        if (position == 7) return SUBJECT_ECON;
        return SUBJECT_ALL;
    }

    private String textOf(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private String safeMessage(Exception e, int fallbackRes) {
        String message = e == null ? null : e.getMessage();
        return message == null || message.trim().isEmpty() ? getString(fallbackRes) : message;
    }

    private String formatNetworkResponseError(Response<?> response) {
        if (response == null) {
            return getString(R.string.status_network_error);
        }
        return getString(R.string.text_http_error, response.code());
    }

    private String formatNetworkFailure(Throwable throwable) {
        if (throwable == null) {
            return getString(R.string.status_network_error);
        }
        String message = throwable.getMessage();
        String lower = message == null ? "" : message.toLowerCase(Locale.US);
        if (lower.contains("unable to resolve host") || lower.contains("no address associated with hostname")) {
            return getString(R.string.message_network_dns_error);
        }
        if (lower.contains("timeout")) {
            return getString(R.string.message_network_timeout);
        }
        if (lower.contains("ssl") || lower.contains("handshake")) {
            return getString(R.string.message_network_ssl_error);
        }
        if (message != null && !message.trim().isEmpty()) {
            return message.trim();
        }
        return getString(R.string.status_network_error);
    }

    private void cacheSearchResults(String normalizedQuery, int page, List<PaperItem> items) {
        if (resultsCache == null) {
            return;
        }
        resultsCache.save(
                buildSearchCacheKey(normalizedQuery, page),
                items,
                currentPage,
                totalPages,
                totalResults
        );
    }

    private boolean restoreCachedSearchResults(String normalizedQuery, int page) {
        if (binding == null || resultsCache == null) {
            return false;
        }
        PaperResultsCache.CachedPaperResults cached = resultsCache.load(buildSearchCacheKey(normalizedQuery, page));
        if (cached == null || cached.getItems().isEmpty()) {
            return false;
        }
        currentPage = Math.max(cached.getCurrentPage(), 1);
        totalPages = Math.max(cached.getTotalPages(), 1);
        totalResults = Math.max(cached.getTotalResults(), cached.getItems().size());
        searchResults.clear();
        searchResults.addAll(cached.getItems());
        adapter.setItems(cached.getItems());
        updatePaginationUi();
        showContent(binding);
        scrollResultsToTop();
        Snackbar.make(binding.getRoot(), R.string.message_showing_cached_results, Snackbar.LENGTH_LONG).show();
        return true;
    }

    private String buildSearchCacheKey(String normalizedQuery, int page) {
        return "search|" + normalizedQuery + "|" + selectedSubject + "|" + selectedSort + "|" + selectedResultCount + "|" + page;
    }

    private int resolveTotalResults(ArxivFeed feed, int loadedCount, int page) {
        if (feed != null && feed.getTotalResults() != null && feed.getTotalResults() >= 0) {
            return feed.getTotalResults();
        }
        return Math.max(loadedCount, ((page - 1) * selectedResultCount) + loadedCount);
    }

    private int calculateTotalPages(int total) {
        if (total <= 0) {
            return 0;
        }
        return (total + selectedResultCount - 1) / selectedResultCount;
    }

    private List<Integer> buildVisiblePages() {
        List<Integer> pages = new ArrayList<>();
        if (totalPages <= 5) {
            for (int page = 1; page <= totalPages; page++) {
                pages.add(page);
            }
            return pages;
        }
        pages.add(1);
        if (currentPage <= 2) {
            pages.add(2);
            pages.add(3);
            pages.add(PAGINATION_ELLIPSIS);
            pages.add(totalPages);
            return pages;
        }
        if (currentPage >= totalPages - 2) {
            pages.add(PAGINATION_ELLIPSIS);
            pages.add(totalPages - 2);
            pages.add(totalPages - 1);
            pages.add(totalPages);
            return pages;
        }
        pages.add(PAGINATION_ELLIPSIS);
        pages.add(currentPage - 1);
        pages.add(currentPage);
        pages.add(PAGINATION_ELLIPSIS);
        pages.add(totalPages);
        return pages;
    }

    private Chip createPageChip(int page) {
        Chip chip = new Chip(requireContext());
        chip.setText(String.valueOf(page));
        chip.setCheckable(page == currentPage);
        chip.setChecked(page == currentPage);
        chip.setClickable(page != currentPage && !isPageLoading);
        chip.setEnabled(page != currentPage && !isPageLoading);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(dp(32));
        chip.setMinHeight(dp(32));
        chip.setMinimumHeight(dp(32));
        chip.setMinWidth(0);
        chip.setMinimumWidth(0);
        chip.setTextStartPadding(dp(6));
        chip.setTextEndPadding(dp(6));
        chip.setChipStartPadding(dp(2));
        chip.setChipEndPadding(dp(2));
        chip.setCloseIconVisible(false);
        if (page != currentPage) {
            chip.setOnClickListener(v -> goToSearchPage(page));
        }
        return chip;
    }

    private void scrollResultsToTop() {
        binding.recyclerView.post(() -> binding.recyclerView.scrollToPosition(0));
    }

    private void showAiInsightLoading() {
        ensureAiInsightDialog();
        if (aiInsightSheetBinding == null || aiInsightDialog == null) {
            return;
        }
        aiInsightSheetBinding.aiInsightSheetProgress.setVisibility(View.VISIBLE);
        aiInsightSheetBinding.aiInsightDivider.setVisibility(View.GONE);
        aiInsightSheetBinding.aiInsightSheetActions.setVisibility(View.GONE);
        aiInsightSheetBinding.aiInsightSheetText.setText(R.string.message_ai_analyzing_results);
        aiInsightDialog.show();
    }

    private void ensureAiInsightDialog() {
        if (binding == null || !isAdded()) {
            return;
        }
        if (aiInsightDialog != null && aiInsightSheetBinding != null) {
            return;
        }
        aiInsightSheetBinding = LayoutAiInsightSheetBinding.inflate(getLayoutInflater());
        aiInsightDialog = new BottomSheetDialog(requireContext());
        aiInsightDialog.setContentView(aiInsightSheetBinding.getRoot());
        aiInsightSheetBinding.closeInsightButton.setOnClickListener(v -> aiInsightDialog.dismiss());
        aiInsightSheetBinding.copyInsightButton.setOnClickListener(v -> copyAiInsight());
        aiInsightSheetBinding.shareInsightButton.setOnClickListener(v -> shareAiInsight());
        aiInsightSheetBinding.regenerateInsightButton.setOnClickListener(v -> analyzeSearchResults(true));
        aiInsightDialog.setOnShowListener(dialog -> {
            View bottomSheet = aiInsightDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) {
                return;
            }
            CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) bottomSheet.getLayoutParams();
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            bottomSheet.setLayoutParams(layoutParams);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        aiInsightDialog.setOnDismissListener(dialog -> {
            if (binding != null) {
                binding.aiAnalyzeButton.setEnabled(true);
            }
        });
    }

    private void dismissAiInsightDialog() {
        if (aiInsightDialog != null) {
            aiInsightDialog.dismiss();
            aiInsightDialog = null;
        }
        aiInsightSheetBinding = null;
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private void runOnUiThread(Runnable action) {
        if (binding != null) {
            binding.getRoot().post(action);
        }
    }
}
