package com.in6222.litsync.recommendation;

import com.in6222.litsync.model.PaperItem;

public class RecommendedPaper {

    private final PaperItem paperItem;
    private final String primaryInterest;
    private final String recommendationReason;
    private final int score;

    public RecommendedPaper(PaperItem paperItem, String primaryInterest, String recommendationReason, int score) {
        this.paperItem = paperItem;
        this.primaryInterest = primaryInterest == null ? "" : primaryInterest.trim();
        this.recommendationReason = recommendationReason == null ? "" : recommendationReason.trim();
        this.score = score;
    }

    public PaperItem getPaperItem() {
        return paperItem;
    }

    public String getPrimaryInterest() {
        return primaryInterest;
    }

    public String getRecommendationReason() {
        return recommendationReason;
    }

    public int getScore() {
        return score;
    }
}
