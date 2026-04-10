package com.in6222.litsync.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.room.Room;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.in6222.litsync.R;
import com.in6222.litsync.ai.AiAssistant;
import com.in6222.litsync.database.AppDatabase;
import com.in6222.litsync.databinding.FragmentTrendingBinding;
import com.in6222.litsync.model.ArxivFeed;
import com.in6222.litsync.model.PaperItem;
import com.in6222.litsync.network.RetrofitClient;
import com.in6222.litsync.repository.PaperRepository;
import com.in6222.litsync.util.PaperResultsCache;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TrendingFragment extends BaseFragment {

    private static final String PREFS = "trending_filters";
    private static final String KEY_SUBJECT = "subject";
    private static final String KEY_SORT = "sort";
    private static final String KEY_RESULT_COUNT = "result_count";
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
    private static final int PAGINATION_ELLIPSIS = -1;

    private FragmentTrendingBinding binding;
    private ExecutorService executor;
    private PaperRepository repository;
    private PaperAdapter adapter;
    private AiAssistant aiAssistant;
    private PaperResultsCache resultsCache;
    private SharedPreferences preferences;

    private final List<Integer> resultCountOptions = Arrays.asList(10, 20, 50);
    private String selectedSubject = SUBJECT_CS;
    private String selectedSort = SORT_RELEVANCE;
    private int selectedResultCount = 20;
    private boolean headerCollapsed = false;
    private String activeTagFilter = "";
    private final List<PaperItem> trendingResults = new ArrayList<>();
    private int currentPage = 1;
    private int totalPages = 0;
    private int totalResults = 0;
    private boolean isPageLoading = false;
    private boolean isBlockingLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTrendingBinding.inflate(inflater, container, false);
        executor = Executors.newSingleThreadExecutor();
        AppDatabase database = Room.databaseBuilder(requireContext().getApplicationContext(), AppDatabase.class, "litsync.db").build();
        repository = new PaperRepository(RetrofitClient.getService(), database);
        aiAssistant = AiAssistant.create(requireContext());
        resultsCache = new PaperResultsCache(requireContext());
        preferences = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        adapter = new PaperAdapter(repository, executor, true, aiAssistant);
        adapter.setOnKeywordClickListener(this::applyTagFilter);
        adapter.setPinnedKeyword(activeTagFilter);

        loadSavedState();

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        setupFilterMenus();
        binding.applyFiltersButton.setOnClickListener(v -> loadTrending());
        binding.resetFiltersButton.setOnClickListener(v -> resetFilters());
        binding.collapseHeaderButton.setOnClickListener(v -> setHeaderCollapsed(true));
        binding.expandHeaderButton.setOnClickListener(v -> setHeaderCollapsed(false));
        binding.tagFilterChip.setOnCloseIconClickListener(v -> clearTagFilter());
        binding.firstPageButton.setOnClickListener(v -> goToTrendingPage(1));
        binding.previousPageButton.setOnClickListener(v -> goToTrendingPage(currentPage - 1));
        binding.nextPageButton.setOnClickListener(v -> goToTrendingPage(currentPage + 1));

        updateTagFilterUi();
        applyHeaderCollapseState();
        updatePaginationUi();
        loadTrending();
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
        binding = null;
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

    private void loadTrending() {
        requestTrendingPage(1, true);
    }

    private void goToTrendingPage(int page) {
        if (binding == null || isPageLoading || totalPages == 0) {
            return;
        }
        int targetPage = Math.max(1, Math.min(page, totalPages));
        if (targetPage == currentPage) {
            return;
        }
        requestTrendingPage(targetPage, false);
    }

    private void requestTrendingPage(int page, boolean resetResults) {
        if (binding == null || isPageLoading) {
            return;
        }
        int targetPage = Math.max(1, page);
        if (!resetResults && totalPages > 0) {
            targetPage = Math.min(targetPage, totalPages);
        }
        final int requestedPage = targetPage;
        isPageLoading = true;
        isBlockingLoading = resetResults;
        if (resetResults) {
            currentPage = 1;
            totalPages = 0;
            totalResults = 0;
            trendingResults.clear();
            updatePaginationUi();
            showLoading(binding);
            binding.statusText.setText(R.string.status_trending_loading);
        } else {
            updatePaginationUi();
        }
        Call<ArxivFeed> call = repository.searchPapers(
                buildTrendingQuery(),
                (requestedPage - 1) * selectedResultCount,
                selectedResultCount,
                getSortByValue(),
                "descending"
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
                        trendingResults.clear();
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
                List<PaperItem> mapped = mapEntries(feed.getEntries());
                currentPage = requestedPage;
                totalResults = resolveTotalResults(feed, mapped.size(), requestedPage);
                totalPages = calculateTotalPages(totalResults);
                trendingResults.clear();
                trendingResults.addAll(mapped);
                adapter.setItems(mapped);
                cacheTrendingResults(requestedPage, mapped);
                updatePaginationUi();
                showContent(binding);
                scrollResultsToTop();
                if (resetResults) {
                    setHeaderCollapsed(true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ArxivFeed> call, @NonNull Throwable t) {
                if (binding == null) {
                    return;
                }
                isPageLoading = false;
                isBlockingLoading = false;
                updatePaginationUi();
                if (restoreCachedTrendingResults(requestedPage)) {
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

    private String buildTrendingQuery() {
        StringBuilder builder = new StringBuilder();
        if (SUBJECT_CS.equals(selectedSubject)) {
            builder.append("cat:cs.*");
        } else if (SUBJECT_MATH.equals(selectedSubject)) {
            builder.append("cat:math.*");
        } else if (SUBJECT_PHYSICS.equals(selectedSubject)) {
            builder.append("cat:physics.*");
        } else if (SUBJECT_STATISTICS.equals(selectedSubject)) {
            builder.append("cat:stat.*");
        } else if (SUBJECT_EESS.equals(selectedSubject)) {
            builder.append("cat:eess.*");
        } else if (SUBJECT_QBIO.equals(selectedSubject)) {
            builder.append("cat:q-bio.*");
        } else if (SUBJECT_ECON.equals(selectedSubject)) {
            builder.append("cat:econ.*");
        } else {
            builder.append("all:*");
        }

        if (!activeTagFilter.trim().isEmpty()) {
            builder.append(" AND all:").append(activeTagFilter.trim());
        }
        return builder.toString();
    }

    private String getSortByValue() {
        return SORT_LATEST.equals(selectedSort) ? "submittedDate" : "relevance";
    }

    private void applyTagFilter(String keyword) {
        if (binding == null) {
            return;
        }
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            return;
        }
        String previous = activeTagFilter;
        activeTagFilter = normalized;
        updateTagFilterUi();
        updateCollapsedHeaderSummary();
        loadTrending();
        Snackbar.make(binding.getRoot(), getString(R.string.message_trending_tag_filter_applied, normalized), Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, v -> {
                    activeTagFilter = previous;
                    updateTagFilterUi();
                    updateCollapsedHeaderSummary();
                    loadTrending();
                })
                .show();
    }

    private void clearTagFilter() {
        activeTagFilter = "";
        updateTagFilterUi();
        updateCollapsedHeaderSummary();
        loadTrending();
    }

    private void updateTagFilterUi() {
        boolean show = !activeTagFilter.trim().isEmpty();
        binding.tagFilterRow.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.tagFilterChip.setText(activeTagFilter);
        if (adapter != null) {
            adapter.setPinnedKeyword(activeTagFilter);
        }
    }

    private void updatePaginationUi() {
        if (binding == null) {
            return;
        }
        boolean hasMultiplePages = totalPages > 1;
        boolean showContainer = !isBlockingLoading && !trendingResults.isEmpty() && (hasMultiplePages || isPageLoading);
        binding.paginationContainer.setVisibility(showContainer ? View.VISIBLE : View.GONE);
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
        if (showContainer && totalPages > 0) {
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

    private void resetFilters() {
        selectedSubject = SUBJECT_CS;
        selectedSort = SORT_RELEVANCE;
        selectedResultCount = 20;
        activeTagFilter = "";
        binding.subjectFilterInput.setText(getSubjectLabel(selectedSubject), false);
        binding.sortFilterInput.setText(getSortLabel(selectedSort), false);
        binding.resultCountInput.setText(String.valueOf(selectedResultCount), false);
        updateTagFilterUi();
        saveState();
        updateCollapsedHeaderSummary();
        loadTrending();
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
    }

    private void updateCollapsedHeaderSummary() {
        String tag = activeTagFilter.trim().isEmpty() ? getString(R.string.value_none) : activeTagFilter;
        String summary = getString(R.string.text_trending_header_summary,
                getSubjectLabel(selectedSubject),
                getSortLabel(selectedSort),
                String.valueOf(selectedResultCount),
                tag);
        binding.collapsedHeaderSummary.setText(summary);
    }

    private void loadSavedState() {
        selectedSubject = preferences.getString(KEY_SUBJECT, SUBJECT_CS);
        selectedSort = preferences.getString(KEY_SORT, SORT_RELEVANCE);
        selectedResultCount = preferences.getInt(KEY_RESULT_COUNT, 20);
        headerCollapsed = preferences.getBoolean(KEY_HEADER_COLLAPSED, false);
        if (!resultCountOptions.contains(selectedResultCount)) {
            selectedResultCount = 20;
        }
    }

    private void saveState() {
        preferences.edit()
                .putString(KEY_SUBJECT, selectedSubject)
                .putString(KEY_SORT, selectedSort)
                .putInt(KEY_RESULT_COUNT, selectedResultCount)
                .putBoolean(KEY_HEADER_COLLAPSED, headerCollapsed)
                .apply();
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
            chip.setOnClickListener(v -> goToTrendingPage(page));
        }
        return chip;
    }

    private void scrollResultsToTop() {
        binding.recyclerView.post(() -> binding.recyclerView.scrollToPosition(0));
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
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

    private void cacheTrendingResults(int page, List<PaperItem> items) {
        if (resultsCache == null) {
            return;
        }
        resultsCache.save(
                buildTrendingCacheKey(page),
                items,
                currentPage,
                totalPages,
                totalResults
        );
    }

    private boolean restoreCachedTrendingResults(int page) {
        if (binding == null || resultsCache == null) {
            return false;
        }
        PaperResultsCache.CachedPaperResults cached = resultsCache.load(buildTrendingCacheKey(page));
        if (cached == null || cached.getItems().isEmpty()) {
            return false;
        }
        currentPage = Math.max(cached.getCurrentPage(), 1);
        totalPages = Math.max(cached.getTotalPages(), 1);
        totalResults = Math.max(cached.getTotalResults(), cached.getItems().size());
        trendingResults.clear();
        trendingResults.addAll(cached.getItems());
        adapter.setItems(cached.getItems());
        updatePaginationUi();
        showContent(binding);
        scrollResultsToTop();
        Snackbar.make(binding.getRoot(), R.string.message_showing_cached_results, Snackbar.LENGTH_LONG).show();
        return true;
    }

    private String buildTrendingCacheKey(int page) {
        return "trending|" + selectedSubject + "|" + selectedSort + "|" + selectedResultCount + "|" + activeTagFilter + "|" + page;
    }

    public void handlePrimaryTabReselected() {
        if (binding == null) {
            return;
        }
        if (!activeTagFilter.trim().isEmpty()) {
            clearTagFilter();
            return;
        }
        scrollResultsToTop();
    }

    public boolean handleBackPressed() {
        if (binding == null) {
            return false;
        }
        if (!activeTagFilter.trim().isEmpty()) {
            clearTagFilter();
            return true;
        }
        return false;
    }
}
