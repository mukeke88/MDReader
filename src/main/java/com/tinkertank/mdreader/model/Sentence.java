package com.tinkertank.mdreader.model;

public class Sentence {

    private int id;
    private int paragraphId;
    private String text;
    private String explanation;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getParagraphId() {
        return paragraphId;
    }

    public void setParagraphId(int paragraphId) {
        this.paragraphId = paragraphId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
