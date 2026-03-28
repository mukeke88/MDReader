package com.tinkertank.mdreader.repository;

import com.tinkertank.mdreader.model.ReadingProgress;

public interface ProgressRepository {

    ReadingProgress findByChapterId(String chapterId);

    ReadingProgress save(String chapterId, ReadingProgress progress);
}
