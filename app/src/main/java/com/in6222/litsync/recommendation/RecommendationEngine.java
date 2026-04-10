package com.in6222.litsync.recommendation;

import android.content.Context;

import com.in6222.litsync.R;
import com.in6222.litsync.database.FavoritePaper;
import com.in6222.litsync.model.ArxivFeed;
import com.in6222.litsync.model.PaperItem;
import com.in6222.litsync.network.RetrofitClient;
import com.in6222.litsync.ui.BookmarkMetadataStore;
import com.in6222.litsync.util.ArxivPaperMapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Response;

public class RecommendationEngine {

    private static final int INTEREST_LIMIT = 3;
    private static final int CANDIDATE_LIMIT_PER_INTEREST = 12;
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "about", "after", "also", "among", "and", "approach", "based", "between",
            "from", "have", "into", "more", "paper", "papers", "result", "results",
            "show", "shows", "that", "their", "these", "this", "using", "with", "within",
            "without", "which", "where", "while", "been", "being", "such", "than", "then",
            "they", "them", "over", "under", "your", "ours", "the", "for", "are", "was",
            "were", "will", "can", "may", "our", "its", "via", "new", "study", "method",
            "methods", "learning", "research"
    ));

    private final Context context;
    private final BookmarkMetadataStore metadataStore;
    private final RecommendationStore recommendationStore;

    public RecommendationEngine(Context context) {
        this.context = context.getApplicationContext();
        this.metadataStore = new BookmarkMetadataStore(this.context);
        this.recommendationStore = new RecommendationStore(this.context);
    }

    public List<RecommendedPaper> generate(List<FavoritePaper> favorites) throws IOException {
        if (favorites == null || favorites.isEmpty()) {
            return Collections.emptyList();
        }
        InterestProfile profile = buildInterestProfile(favorites);
        if (profile.topInterests.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (String interest : profile.topInterests) {
            for (PaperItem paperItem : fetchCandidates(interest)) {
                String key = dedupeKey(paperItem);
                if (candidates.containsKey(key)) {
                    continue;
                }
                Candidate candidate = scoreCandidate(paperItem, interest, profile);
                if (candidate.score > 0) {
                    candidates.put(key, candidate);
                }
            }
        }

        List<Candidate> rankedCandidates = new ArrayList<>(candidates.values());
        rankedCandidates.sort(Comparator
                .comparingInt((Candidate candidate) -> candidate.score).reversed()
                .thenComparingInt(candidate -> candidate.ageInDays)
                .thenComparing(candidate -> candidate.paperItem.getTitle(), String.CASE_INSENSITIVE_ORDER));

        return pickFinalRecommendations(rankedCandidates);
    }

    private List<PaperItem> fetchCandidates(String interest) throws IOException {
        String searchQuery = buildArxivQuery(interest);
        Response<ArxivFeed> response = RetrofitClient.getService()
                .searchPapers(searchQuery, 0, CANDIDATE_LIMIT_PER_INTEREST, "submittedDate", "descending")
                .execute();
        if (!response.isSuccessful() || response.body() == null) {
            return Collections.emptyList();
        }
        return ArxivPaperMapper.map(response.body().getEntries());
    }

    private Candidate scoreCandidate(PaperItem paperItem, String seedInterest, InterestProfile profile) {
        String paperKey = dedupeKey(paperItem);
        if (profile.bookmarkedKeys.contains(paperKey)) {
            return new Candidate(paperItem, seedInterest, -100, Integer.MAX_VALUE, Collections.emptyList());
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();

        String primaryInterest = findMatchingInterest(paperItem, profile.topInterests);
        if (primaryInterest.isEmpty()) {
            primaryInterest = seedInterest;
        } else {
            score += 5;
            reasons.add(context.getString(R.string.reason_matched_tag, primaryInterest));
        }

        int ageInDays = calculateAgeInDays(paperItem.getPublishedDate());
        if (ageInDays <= 2) {
            score += 3;
            reasons.add(context.getString(R.string.reason_recent_paper, 2));
        } else if (ageInDays <= 7) {
            score += 2;
            reasons.add(context.getString(R.string.reason_recent_paper, 7));
        }

        int overlapCount = countKeywordOverlap(paperItem, profile.favoriteKeywords);
        if (overlapCount > 0) {
            score += Math.min(4, overlapCount * 2);
            reasons.add(context.getString(R.string.reason_similar_interest));
        }

        if (profile.notifiedKeys.contains(paperKey)) {
            score -= 40;
        }

        return new Candidate(paperItem, primaryInterest, score, ageInDays, reasons);
    }

    private List<RecommendedPaper> pickFinalRecommendations(List<Candidate> rankedCandidates) {
        List<RecommendedPaper> selected = new ArrayList<>();
        Set<String> usedInterests = new LinkedHashSet<>();

        for (Candidate candidate : rankedCandidates) {
            if (selected.size() >= 3) {
                break;
            }
            if (!candidate.primaryInterest.isEmpty() && !usedInterests.contains(candidate.primaryInterest)) {
                selected.add(candidate.toRecommendedPaper());
                usedInterests.add(candidate.primaryInterest);
                continue;
            }
            if (selected.size() < 2) {
                selected.add(candidate.toRecommendedPaper());
                if (!candidate.primaryInterest.isEmpty()) {
                    usedInterests.add(candidate.primaryInterest);
                }
            }
        }

        for (Candidate candidate : rankedCandidates) {
            if (selected.size() >= 3) {
                break;
            }
            if (containsPaper(selected, candidate.paperItem)) {
                continue;
            }
            List<String> reasons = new ArrayList<>(candidate.reasons);
            if (!candidate.primaryInterest.isEmpty() && !usedInterests.contains(candidate.primaryInterest)) {
                reasons.add(context.getString(R.string.reason_diverse_pick));
                usedInterests.add(candidate.primaryInterest);
            }
            selected.add(new RecommendedPaper(
                    candidate.paperItem,
                    candidate.primaryInterest,
                    joinReasons(reasons),
                    candidate.score
            ));
        }

        return selected;
    }

    private boolean containsPaper(List<RecommendedPaper> recommendations, PaperItem paperItem) {
        for (RecommendedPaper recommendation : recommendations) {
            if (dedupeKey(recommendation.getPaperItem()).equals(dedupeKey(paperItem))) {
                return true;
            }
        }
        return false;
    }

    private String joinReasons(List<String> reasons) {
        StringBuilder builder = new StringBuilder();
        for (String reason : reasons) {
            if (reason == null || reason.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" • ");
            }
            builder.append(reason.trim());
        }
        return builder.toString();
    }

    private InterestProfile buildInterestProfile(List<FavoritePaper> favorites) {
        Map<String, Integer> interestScores = new HashMap<>();
        Set<String> bookmarkedKeys = new HashSet<>();
        Set<String> favoriteKeywords = new LinkedHashSet<>();
        Set<String> notifiedKeys = new HashSet<>(recommendationStore.getNotifiedLinks());

        for (FavoritePaper favorite : favorites) {
            PaperItem paperItem = new PaperItem(
                    favorite.getId(),
                    favorite.getTitle(),
                    favorite.getAuthor(),
                    favorite.getSummary(),
                    favorite.getPublishedDate(),
                    favorite.getLink()
            );
            bookmarkedKeys.add(dedupeKey(paperItem));
            addWeightedInterests(interestScores, metadataStore.getTags(paperItem), 4);
            addWeightedInterests(interestScores, metadataStore.getGroup(paperItem), 2);
            favoriteKeywords.addAll(extractKeywords(favorite.getTitle() + " " + favorite.getSummary(), 10));
        }

        if (interestScores.isEmpty()) {
            for (String keyword : favoriteKeywords) {
                interestScores.put(keyword, interestScores.getOrDefault(keyword, 0) + 1);
            }
        }

        List<Map.Entry<String, Integer>> sortedInterests = new ArrayList<>(interestScores.entrySet());
        sortedInterests.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry::getKey));

        List<String> topInterests = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedInterests) {
            if (entry.getKey().trim().isEmpty()) {
                continue;
            }
            topInterests.add(entry.getKey());
            if (topInterests.size() == INTEREST_LIMIT) {
                break;
            }
        }

        return new InterestProfile(topInterests, bookmarkedKeys, favoriteKeywords, notifiedKeys);
    }

    private void addWeightedInterests(Map<String, Integer> interestScores, String rawValues, int weight) {
        if (rawValues == null || rawValues.trim().isEmpty()) {
            return;
        }
        String[] parts = rawValues.split(",");
        for (String part : parts) {
            String normalized = normalizeInterest(part);
            if (normalized.isEmpty()) {
                continue;
            }
            interestScores.put(normalized, interestScores.getOrDefault(normalized, 0) + weight);
        }
    }

    private String normalizeInterest(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private List<String> extractKeywords(String text, int limit) {
        Map<String, Integer> keywordScores = new LinkedHashMap<>();
        if (text == null) {
            return Collections.emptyList();
        }
        String[] words = text.toLowerCase(Locale.US).replaceAll("[^a-z0-9#+-]", " ").split("\\s+");
        for (String word : words) {
            if (!isKeywordCandidate(word)) {
                continue;
            }
            keywordScores.put(word, keywordScores.getOrDefault(word, 0) + 1);
        }
        List<Map.Entry<String, Integer>> sortedKeywords = new ArrayList<>(keywordScores.entrySet());
        sortedKeywords.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry::getKey));
        List<String> keywords = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedKeywords) {
            keywords.add(entry.getKey());
            if (keywords.size() == limit) {
                break;
            }
        }
        return keywords;
    }

    private boolean isKeywordCandidate(String word) {
        if (word == null || word.length() < 3) {
            return false;
        }
        return !STOP_WORDS.contains(word);
    }

    private String findMatchingInterest(PaperItem paperItem, List<String> interests) {
        String haystack = (paperItem.getTitle() + " " + paperItem.getSummary()).toLowerCase(Locale.US);
        for (String interest : interests) {
            String normalized = interest.toLowerCase(Locale.US);
            if (haystack.contains(normalized)) {
                return interest;
            }
        }
        return "";
    }

    private int countKeywordOverlap(PaperItem paperItem, Set<String> favoriteKeywords) {
        if (favoriteKeywords.isEmpty()) {
            return 0;
        }
        Set<String> paperKeywords = new HashSet<>(extractKeywords(paperItem.getTitle() + " " + paperItem.getSummary(), 12));
        int overlap = 0;
        for (String favoriteKeyword : favoriteKeywords) {
            if (paperKeywords.contains(favoriteKeyword)) {
                overlap++;
            }
        }
        return overlap;
    }

    private int calculateAgeInDays(String publishedDate) {
        try {
            OffsetDateTime published = OffsetDateTime.parse(publishedDate);
            long days = ChronoUnit.DAYS.between(published.toLocalDate(), OffsetDateTime.now().toLocalDate());
            return (int) Math.max(days, 0);
        } catch (Exception exception) {
            return Integer.MAX_VALUE / 2;
        }
    }

    private String buildArxivQuery(String interest) {
        return "all:\"" + interest + "\"";
    }

    private String dedupeKey(PaperItem paperItem) {
        String link = paperItem.getLink() == null ? "" : paperItem.getLink().trim();
        if (!link.isEmpty()) {
            return link;
        }
        return (paperItem.getTitle() == null ? "" : paperItem.getTitle().trim())
                + "|"
                + (paperItem.getPublishedDate() == null ? "" : paperItem.getPublishedDate().trim());
    }

    private static class InterestProfile {
        private final List<String> topInterests;
        private final Set<String> bookmarkedKeys;
        private final Set<String> favoriteKeywords;
        private final Set<String> notifiedKeys;

        private InterestProfile(List<String> topInterests, Set<String> bookmarkedKeys, Set<String> favoriteKeywords, Set<String> notifiedKeys) {
            this.topInterests = topInterests;
            this.bookmarkedKeys = bookmarkedKeys;
            this.favoriteKeywords = favoriteKeywords;
            this.notifiedKeys = notifiedKeys;
        }
    }

    private class Candidate {
        private final PaperItem paperItem;
        private final String primaryInterest;
        private final int score;
        private final int ageInDays;
        private final List<String> reasons;

        private Candidate(PaperItem paperItem, String primaryInterest, int score, int ageInDays, List<String> reasons) {
            this.paperItem = paperItem;
            this.primaryInterest = primaryInterest == null ? "" : primaryInterest.trim();
            this.score = score;
            this.ageInDays = ageInDays;
            this.reasons = reasons;
        }

        private RecommendedPaper toRecommendedPaper() {
            return new RecommendedPaper(paperItem, primaryInterest, joinReasons(reasons), score);
        }
    }
}
