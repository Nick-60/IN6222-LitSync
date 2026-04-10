package com.in6222.litsync.model;

import com.tickaroo.tikxml.annotation.Element;
import com.tickaroo.tikxml.annotation.PropertyElement;
import com.tickaroo.tikxml.annotation.Xml;
import java.util.List;

/**
 * arXiv Atom Feed 根节点。
 */
@Xml(name = "feed")
public class ArxivFeed {

    @PropertyElement(name = "title")
    private String title;

    @PropertyElement(name = "updated")
    private String updated;

    @PropertyElement(name = "opensearch:totalResults")
    private Integer totalResults;

    @PropertyElement(name = "opensearch:startIndex")
    private Integer startIndex;

    @PropertyElement(name = "opensearch:itemsPerPage")
    private Integer itemsPerPage;

    @Element(name = "entry")
    private List<ArxivEntry> entries;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public Integer getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(Integer totalResults) {
        this.totalResults = totalResults;
    }

    public Integer getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }

    public Integer getItemsPerPage() {
        return itemsPerPage;
    }

    public void setItemsPerPage(Integer itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }

    public List<ArxivEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ArxivEntry> entries) {
        this.entries = entries;
    }
}
