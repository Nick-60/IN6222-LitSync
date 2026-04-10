package com.in6222.litsync.ai;

import android.content.Context;

import com.in6222.litsync.model.PaperItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AiAssistant {

    private final AiConfig config;
    private final AiProvider provider;
    private final AiResponseCache cache;

    public AiAssistant(AiConfig config, AiProvider provider, AiResponseCache cache) {
        this.config = config;
        this.provider = provider;
        this.cache = cache;
    }

    public static AiAssistant create(Context context) {
        AiConfig config = new AiSettingsStore(context).load();
        return new AiAssistant(config, createProvider(config), AiResponseCache.getInstance());
    }

    public boolean isAvailable() {
        return config.isConfigured();
    }

    public String summarizePaper(PaperItem item) throws IOException {
        String cacheKey = buildCacheKey(
                "paper_v3",
                safeText(item.getTitle()),
                safeText(item.getAuthor()),
                safeText(item.getSummary())
        );
        return sanitizePaperSummary(
                request(
                cacheKey,
                "You explain research papers clearly for students. "
                        + languageInstruction()
                        + " Return only the final answer. Do not mention instructions, roles, constraints, analysis steps, or drafting process. "
                        + "Use exactly three short sections titled Idea, Why it matters, and What to notice. "
                        + "Each section should contain exactly one short paragraph with 1 or 2 simple sentences. "
                        + "Rephrase the abstract in plain student-friendly language instead of copying long phrases. "
                        + "Focus on the problem, the value, and one practical reading tip. Avoid markdown symbols such as **, #, or nested bullets.",
                "Paper title: " + safeText(item.getTitle()) + "\n"
                        + "Authors: " + safeText(item.getAuthor()) + "\n"
                        + "Abstract: " + safeText(item.getSummary()),
                260,
                0.3
        ),
                item
        );
    }

    public String regeneratePaperSummary(PaperItem item) throws IOException {
        String cacheKey = buildCacheKey(
                "paper_v3",
                safeText(item.getTitle()),
                safeText(item.getAuthor()),
                safeText(item.getSummary())
        );
        cache.remove(cacheKey);
        return summarizePaper(item);
    }

    public String rewriteSearchQuery(String naturalLanguageQuery) throws IOException {
        String response = request(
                buildCacheKey("rewrite", safeText(naturalLanguageQuery)),
                "You optimize academic search queries for arXiv. "
                        + languageInstruction()
                        + " Return only one compact arXiv-ready search query line with 3 to 8 important keywords or short phrases. "
                        + "Do not reveal analysis, reasoning, steps, labels, markdown, bullets, numbering, or XML/JSON. "
                        + "Do not start with words like Analyze, Intent, Topic, Output, Query, or Search. "
                        + "Only output the final query itself.",
                "User search intent: " + safeText(naturalLanguageQuery),
                80,
                0.2
        );
        return sanitizeSearchQuery(response, naturalLanguageQuery);
    }

    public String summarizeSearchResults(String originalQuery, List<PaperItem> items) throws IOException {
        List<PaperItem> limitedItems = new ArrayList<>();
        if (items != null) {
            limitedItems.addAll(items.subList(0, Math.min(items.size(), 5)));
        }
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Original search query: ").append(safeText(originalQuery)).append("\n\n");
        for (int index = 0; index < limitedItems.size(); index++) {
            PaperItem item = limitedItems.get(index);
            promptBuilder.append("Paper ").append(index + 1).append(":\n");
            promptBuilder.append("Title: ").append(safeText(item.getTitle())).append("\n");
            promptBuilder.append("Authors: ").append(safeText(item.getAuthor())).append("\n");
            promptBuilder.append("Abstract: ").append(safeText(item.getSummary())).append("\n\n");
        }
        return sanitizeSearchResultsSummary(
                request(
                buildCacheKey("results", safeText(originalQuery), promptBuilder.toString().trim()),
                "You analyze groups of research papers for students. "
                        + languageInstruction()
                        + " Write a concise search-results insight for a paper discovery app. "
                        + "Return only the final answer, with no markdown symbols, bullets, or headings like ##. "
                        + "Use exactly three short sections titled Main directions, Where to start, and Refine this search. "
                        + "Main directions: summarize the common themes across the papers in 2 to 3 sentences. "
                        + "Where to start: recommend 1 or 2 papers and explain who should read them first. Refer to papers by title, not by number labels like Paper 1 or Paper 2. "
                        + "Refine this search: if the query is broad, suggest 2 or 3 more specific search phrases; otherwise give one short next-step tip. "
                        + "Keep it practical, natural, and app-friendly.",
                promptBuilder.toString().trim(),
                420,
                0.35
        ), limitedItems);
    }

    public String regenerateSearchResultsSummary(String originalQuery, List<PaperItem> items) throws IOException {
        List<PaperItem> limitedItems = new ArrayList<>();
        if (items != null) {
            limitedItems.addAll(items.subList(0, Math.min(items.size(), 5)));
        }
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Original search query: ").append(safeText(originalQuery)).append("\n\n");
        for (int index = 0; index < limitedItems.size(); index++) {
            PaperItem item = limitedItems.get(index);
            promptBuilder.append("Paper ").append(index + 1).append(":\n");
            promptBuilder.append("Title: ").append(safeText(item.getTitle())).append("\n");
            promptBuilder.append("Authors: ").append(safeText(item.getAuthor())).append("\n");
            promptBuilder.append("Abstract: ").append(safeText(item.getSummary())).append("\n\n");
        }
        String cacheKey = buildCacheKey("results", safeText(originalQuery), promptBuilder.toString().trim());
        cache.remove(cacheKey);
        return summarizeSearchResults(originalQuery, items);
    }

    private String request(String cacheKey, String systemPrompt, String userPrompt, int maxTokens, double temperature) throws IOException {
        if (!isAvailable()) {
            throw new IllegalStateException("AI is not configured");
        }
        String cachedValue = cache.get(cacheKey);
        if (cachedValue != null && !cachedValue.trim().isEmpty()) {
            return cachedValue;
        }
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", systemPrompt));
        messages.add(new AiMessage("user", userPrompt));
        String response = provider.complete(messages, maxTokens, temperature).trim();
        cache.put(cacheKey, response);
        return response;
    }

    private static AiProvider createProvider(AiConfig config) {
        return new OpenAiCompatibleAiProvider(config);
    }

    private String languageInstruction() {
        return Locale.getDefault().getLanguage().equalsIgnoreCase("zh")
                ? "Respond in Simplified Chinese."
                : "Respond in English.";
    }

    private String safeText(String text) {
        return text == null || text.trim().isEmpty() ? "N/A" : text.trim();
    }

    private String sanitizeSingleLine(String text) {
        String sanitized = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        while (sanitized.contains("  ")) {
            sanitized = sanitized.replace("  ", " ");
        }
        if (sanitized.startsWith("\"") && sanitized.endsWith("\"") && sanitized.length() > 1) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }
        return sanitized;
    }

    private String sanitizeSearchQuery(String response, String originalQuery) {
        String sanitized = sanitizeSingleLine(response);
        if (sanitized.isEmpty()) {
            return buildFallbackSearchQuery(originalQuery);
        }
        sanitized = sanitized
                .replace("**", " ")
                .replace("`", " ")
                .replace("\"", " ")
                .replace("'", " ")
                .trim();
        sanitized = sanitized.replaceAll("(?i)^search\\s+query\\s*[:：-]?\\s*", "");
        sanitized = sanitized.replaceAll("(?i)^query\\s*[:：-]?\\s*", "");
        sanitized = sanitized.replaceAll("(?i)^final\\s+query\\s*[:：-]?\\s*", "");
        sanitized = sanitized.replaceAll("(?i)^arxiv\\s+query\\s*[:：-]?\\s*", "");
        if (looksLikeReasoningLeak(sanitized)) {
            return buildFallbackSearchQuery(originalQuery);
        }
        sanitized = sanitized.replaceAll("(?i)\\b(and|or|the|a|an|please|find|latest|recent|about)\\b", " ");
        sanitized = sanitized.replaceAll("[^\\p{L}\\p{N}\\s:/+._-]", " ");
        sanitized = sanitized.replaceAll("\\s{2,}", " ").trim();
        if (sanitized.isEmpty() || looksLikeReasoningLeak(sanitized)) {
            return buildFallbackSearchQuery(originalQuery);
        }
        return sanitized;
    }

    private boolean looksLikeReasoningLeak(String text) {
        if (text == null) {
            return true;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        return normalized.contains("analyze the user's request")
                || normalized.contains("analyze the user request")
                || normalized.contains("core topic")
                || normalized.contains("intent:")
                || normalized.contains("output format")
                || normalized.contains("step 1")
                || normalized.contains("reasoning")
                || normalized.contains("compact search query line")
                || normalized.contains("target platform")
                || normalized.matches("^(\\d+\\.|[-*]).*")
                || normalized.contains("**")
                || normalized.contains("search query line");
    }

    private String buildFallbackSearchQuery(String originalQuery) {
        String source = safeText(originalQuery);
        if ("N/A".equals(source)) {
            return "";
        }
        String normalized = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (normalized.isEmpty()) {
            return "";
        }
        String[] stopwords = {
                "i", "me", "my", "we", "our", "want", "find", "look", "search", "about",
                "for", "the", "a", "an", "of", "to", "in", "on", "with", "and", "or",
                "is", "are", "latest", "recent", "progress", "advances", "papers", "paper",
                "有关", "关于", "我想", "我想找", "找找", "最新", "进展", "论文", "相关", "方面", "的"
        };
        for (String stopword : stopwords) {
            normalized = normalized.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(stopword) + "\\b", " ");
        }
        normalized = normalized.replaceAll("\\s{2,}", " ").trim();
        if (normalized.isEmpty()) {
            return source.trim();
        }
        String[] tokens = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String token : tokens) {
            if (token == null || token.trim().isEmpty()) {
                continue;
            }
            if (builder.indexOf(token) >= 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(token.trim());
            count++;
            if (count >= 6) {
                break;
            }
        }
        String fallback = builder.toString().trim();
        return fallback.isEmpty() ? source.trim() : fallback;
    }

    private String sanitizePaperSummary(String response, PaperItem item) {
        String sanitized = response == null ? "" : response.replace("**", "").replace("\r\n", "\n").trim();
        if (sanitized.isEmpty()) {
            return buildFallbackPaperSummary(item);
        }
        if (sanitized.contains("Analyze the Request")
                || sanitized.contains("Analyze the Paper")
                || sanitized.contains("Drafting the Content")
                || sanitized.contains("Constraint 1")) {
            return buildFallbackPaperSummary(item);
        }
        while (sanitized.contains("\n\n\n")) {
            sanitized = sanitized.replace("\n\n\n", "\n\n");
        }
        return sanitized;
    }

    private String sanitizeSearchResultsSummary(String response, List<PaperItem> items) {
        String sanitized = response == null ? "" : response.replace("**", "").replace("\r\n", "\n").trim();
        if (sanitized.isEmpty()) {
            return "";
        }
        sanitized = sanitized.replaceAll("(?m)^#{1,6}\\s*", "");
        sanitized = sanitized.replaceAll("(?m)^\\s*[-*]\\s*", "");
        sanitized = sanitized.replaceAll("(?m)^\\s*\\d+\\.\\s*", "");
        if (items != null) {
            for (int index = 0; index < items.size(); index++) {
                PaperItem item = items.get(index);
                String title = safeTitleForInlineReference(item == null ? null : item.getTitle());
                if (title.isEmpty()) {
                    continue;
                }
                sanitized = sanitized.replace("Paper " + (index + 1), title);
                sanitized = sanitized.replace("paper " + (index + 1), title);
            }
        }
        while (sanitized.contains("\n\n\n")) {
            sanitized = sanitized.replace("\n\n\n", "\n\n");
        }
        return sanitized.trim();
    }

    private String safeTitleForInlineReference(String title) {
        String value = safeText(title);
        if ("N/A".equals(value)) {
            return "";
        }
        return "\"" + value + "\"";
    }

    private String buildFallbackPaperSummary(PaperItem item) {
        String abstractText = safeText(item.getSummary());
        String title = safeText(item.getTitle());
        boolean chinese = Locale.getDefault().getLanguage().equalsIgnoreCase("zh");
        if ("N/A".equals(abstractText)) {
            return chinese
                    ? "核心内容\n这篇论文围绕“" + title + "”展开。\n\n为什么重要\n它讨论了一个值得继续阅读的研究问题。\n\n阅读提示\n建议重点关注方法、数据集和实验结果。"
                    : "Idea\nThis paper focuses on \"" + title + "\".\n\nWhy it matters\nIt addresses a research problem worth exploring further.\n\nWhat to notice\nPay attention to the method, dataset, and reported results.";
        }
        String[] parts = abstractText.split("(?<=[.!?。！？])\\s+");
        String firstSentence = parts.length > 0 ? parts[0].trim() : abstractText;
        String secondSentence = parts.length > 1 ? parts[1].trim() : firstSentence;
        if (chinese) {
            return "核心内容\n" + firstSentence
                    + "\n\n为什么重要\n" + secondSentence
                    + "\n\n阅读提示\n建议重点关注所用方法、数据来源以及实验表现。";
        }
        return "Idea\n" + firstSentence
                + "\n\nWhy it matters\n" + secondSentence
                + "\n\nWhat to notice\nPay attention to the method, dataset, and reported results.";
    }

    private String buildCacheKey(String prefix, String... parts) {
        StringBuilder builder = new StringBuilder();
        builder.append(config.getProviderType().name())
                .append('|')
                .append(config.getRawBaseUrl())
                .append('|')
                .append(config.getModel())
                .append('|')
                .append(prefix);
        if (parts != null) {
            for (String part : parts) {
                builder.append('|').append(safeText(part));
            }
        }
        return builder.toString();
    }
}
