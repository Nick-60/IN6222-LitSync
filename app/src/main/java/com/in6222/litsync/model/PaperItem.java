package com.in6222.litsync.model;

public class PaperItem {

    private int id;
    private String title;
    private String author;
    private String summary;
    private String publishedDate;
    private String link;

    public PaperItem(int id, String title, String author, String summary, String publishedDate, String link) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.summary = summary;
        this.publishedDate = publishedDate;
        this.link = link;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getSummary() {
        return summary;
    }

    public String getPublishedDate() {
        return publishedDate;
    }

    public String getLink() {
        return link;
    }
}
