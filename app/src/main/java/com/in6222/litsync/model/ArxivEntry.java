package com.in6222.litsync.model;

import com.tickaroo.tikxml.annotation.Element;
import com.tickaroo.tikxml.annotation.PropertyElement;
import com.tickaroo.tikxml.annotation.Xml;
import java.util.List;

/**
 * arXiv 论文条目。
 */
@Xml(name = "entry")
public class ArxivEntry {

    @PropertyElement(name = "title")
    private String title;

    @PropertyElement(name = "summary")
    private String summary;

    @PropertyElement(name = "published")
    private String published;

    @Element(name = "author")
    private List<ArxivAuthor> authors;

    @Element(name = "link")
    private List<ArxivLink> links;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPublished() {
        return published;
    }

    public void setPublished(String published) {
        this.published = published;
    }

    public List<ArxivAuthor> getAuthors() {
        return authors;
    }

    public void setAuthors(List<ArxivAuthor> authors) {
        this.authors = authors;
    }

    public List<ArxivLink> getLinks() {
        return links;
    }

    public void setLinks(List<ArxivLink> links) {
        this.links = links;
    }
}
