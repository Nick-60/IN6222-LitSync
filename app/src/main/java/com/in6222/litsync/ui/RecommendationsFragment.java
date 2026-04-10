package com.in6222.litsync.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.room.Room;

import com.in6222.litsync.R;
import com.in6222.litsync.ai.AiAssistant;
import com.in6222.litsync.database.AppDatabase;
import com.in6222.litsync.databinding.FragmentRecommendationsBinding;
import com.in6222.litsync.model.PaperItem;
import com.in6222.litsync.network.RetrofitClient;
import com.in6222.litsync.recommendation.RecommendationStore;
import com.in6222.litsync.recommendation.RecommendedPaper;
import com.in6222.litsync.repository.PaperRepository;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecommendationsFragment extends Fragment {

    private FragmentRecommendationsBinding binding;
    private ExecutorService executor;
    private PaperAdapter adapter;
    private AiAssistant aiAssistant;
    private RecommendationStore recommendationStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRecommendationsBinding.inflate(inflater, container, false);
        executor = Executors.newSingleThreadExecutor();
        AppDatabase database = Room.databaseBuilder(requireContext().getApplicationContext(), AppDatabase.class, "litsync.db").build();
        PaperRepository repository = new PaperRepository(RetrofitClient.getService(), database);
        aiAssistant = AiAssistant.create(requireContext());
        recommendationStore = new RecommendationStore(requireContext());
        adapter = new PaperAdapter(repository, executor, true, aiAssistant);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        loadRecommendations();
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
        loadRecommendations();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) {
            executor.shutdown();
        }
        binding = null;
    }

    private void loadRecommendations() {
        if (binding == null || recommendationStore == null) {
            return;
        }
        showLoadingState();
        executor.execute(() -> {
            List<RecommendedPaper> recommendations = recommendationStore.loadRecommendations();
            long generatedAt = recommendationStore.getGeneratedAt();
            List<PaperItem> items = new ArrayList<>();
            Map<String, String> reasons = new LinkedHashMap<>();
            Map<String, String> primaryInterests = new LinkedHashMap<>();
            Map<String, Integer> scores = new LinkedHashMap<>();
            for (RecommendedPaper recommendation : recommendations) {
                PaperItem paperItem = recommendation.getPaperItem();
                String paperKey = buildReasonKey(paperItem);
                items.add(paperItem);
                reasons.put(paperKey, recommendation.getRecommendationReason());
                primaryInterests.put(paperKey, recommendation.getPrimaryInterest());
                scores.put(paperKey, recommendation.getScore());
            }
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }
                adapter.setRecommendationMetadata(reasons, primaryInterests, scores);
                adapter.setItems(items);
                bindGeneratedAt(generatedAt);
                if (items.isEmpty()) {
                    showEmptyState();
                    return;
                }
                showContentState();
            });
        });
    }

    private void bindGeneratedAt(long generatedAt) {
        if (binding == null) {
            return;
        }
        if (generatedAt <= 0L) {
            binding.generatedAtText.setText(R.string.message_recommendation_never_updated);
            return;
        }
        DateFormat dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());
        binding.generatedAtText.setText(getString(R.string.message_recommendation_last_updated, dateTimeFormat.format(new Date(generatedAt))));
    }

    private String buildReasonKey(PaperItem paperItem) {
        String link = paperItem.getLink() == null ? "" : paperItem.getLink().trim();
        if (!link.isEmpty()) {
            return link;
        }
        return (paperItem.getTitle() == null ? "" : paperItem.getTitle().trim())
                + "|"
                + (paperItem.getPublishedDate() == null ? "" : paperItem.getPublishedDate().trim());
    }

    private void showLoadingState() {
        if (binding == null) {
            return;
        }
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.statusText.setVisibility(View.VISIBLE);
        binding.statusText.setText(R.string.status_recommendations_loading);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        if (binding == null) {
            return;
        }
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.VISIBLE);
        binding.statusText.setText(R.string.message_recommendations_empty);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showContentState() {
        if (binding == null) {
            return;
        }
        binding.statusCard.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }
}
