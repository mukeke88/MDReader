package com.tinkertank.mdreader.model;

public class ImportChapterRequest {

    private String markdown;
    private String title;

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
