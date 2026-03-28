package com.tinkertank.mdreader.model;

import java.util.ArrayList;
import java.util.List;

public class ReadingProgress {

    private String chapterId;
    private Integer lastSentenceId;
    private int totalScore;
    private boolean globalExpanded;
    private List<Integer> openedSentenceIds = new ArrayList<Integer>();
    private List<Integer> readSentenceIds = new ArrayList<Integer>();
    private List<Integer> scoredSentenceIds = new ArrayList<Integer>();
    private List<Integer> explanationUsedSentenceIds = new ArrayList<Integer>();

    public String getChapterId() {
        return chapterId;
    }

    public void setChapterId(String chapterId) {
        this.chapterId = chapterId;
    }

    public Integer getLastSentenceId() {
        return lastSentenceId;
    }

    public void setLastSentenceId(Integer lastSentenceId) {
        this.lastSentenceId = lastSentenceId;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public boolean isGlobalExpanded() {
        return globalExpanded;
    }

    public void setGlobalExpanded(boolean globalExpanded) {
        this.globalExpanded = globalExpanded;
    }

    public List<Integer> getOpenedSentenceIds() {
        return openedSentenceIds;
    }

    public void setOpenedSentenceIds(List<Integer> openedSentenceIds) {
        this.openedSentenceIds = openedSentenceIds;
    }

    public List<Integer> getReadSentenceIds() {
        return readSentenceIds;
    }

    public void setReadSentenceIds(List<Integer> readSentenceIds) {
        this.readSentenceIds = readSentenceIds;
    }

    public List<Integer> getScoredSentenceIds() {
        return scoredSentenceIds;
    }

    public void setScoredSentenceIds(List<Integer> scoredSentenceIds) {
        this.scoredSentenceIds = scoredSentenceIds;
    }

    public List<Integer> getExplanationUsedSentenceIds() {
        return explanationUsedSentenceIds;
    }

    public void setExplanationUsedSentenceIds(List<Integer> explanationUsedSentenceIds) {
        this.explanationUsedSentenceIds = explanationUsedSentenceIds;
    }
}
