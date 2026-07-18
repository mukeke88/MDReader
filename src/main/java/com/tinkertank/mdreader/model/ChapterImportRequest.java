package com.tinkertank.mdreader.model;

import javax.validation.constraints.NotBlank;

public class ChapterImportRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String markdown;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }
}
