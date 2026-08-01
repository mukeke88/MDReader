package com.tinkertank.mdreader.repository;

import com.tinkertank.mdreader.model.ReadingProgress;

public interface ProgressRepository {

    ReadingProgress findByUserIdAndChapterId(String userId, String chapterId);

    ReadingProgress save(String userId, String chapterId, ReadingProgress progress);

    void deleteByChapterId(String chapterId);
}
