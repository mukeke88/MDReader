package com.tinkertank.mdreader.repository;

import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.Sentence;
import java.util.List;
import java.util.Optional;

public interface ChapterRepository {

    Optional<ChapterMeta> findChapter(String chapterId);

    List<Sentence> findSentences(String chapterId);
}
