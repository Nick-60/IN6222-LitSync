package com.in6222.litsync.util;

import com.in6222.litsync.model.ArxivAuthor;
import com.in6222.litsync.model.ArxivEntry;
import com.in6222.litsync.model.ArxivLink;
import com.in6222.litsync.model.PaperItem;

import java.util.ArrayList;
import java.util.List;

public class ArxivPaperMapper {

    private ArxivPaperMapper() {
    }

    public static List<PaperItem> map(List<ArxivEntry> entries) {
        List<PaperItem> items = new ArrayList<>();
        if (entries == null) {
            return items;
        }
        for (ArxivEntry entry : entries) {
            items.add(map(entry));
        }
        return items;
    }

    public static PaperItem map(ArxivEntry entry) {
        StringBuilder authors = new StringBuilder();
        List<ArxivAuthor> authorList = entry.getAuthors();
        if (authorList != null) {
            for (int index = 0; index < authorList.size(); index++) {
                if (index > 0) {
                    authors.append(", ");
                }
                authors.append(authorList.get(index).getName());
            }
        }

        String articleLink = "";
        List<ArxivLink> links = entry.getLinks();
        if (links != null) {
            for (ArxivLink link : links) {
                if (link.getHref() != null && !link.getHref().trim().isEmpty()) {
                    articleLink = link.getHref();
                    if ("alternate".equals(link.getRel())) {
                        break;
                    }
                }
            }
        }

        return new PaperItem(
                0,
                safeText(entry.getTitle()),
                authors.toString(),
                safeText(entry.getSummary()),
                safeText(entry.getPublished()),
                articleLink
        );
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }
}
