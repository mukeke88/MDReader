package com.tinkertank.mdreader.repository;

import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.Sentence;
import java.util.List;
import java.util.Optional;

public interface ChapterRepository {

    List<ChapterMeta> findAllChapters();

    Optional<ChapterMeta> findChapter(String chapterId);

    List<Sentence> findSentences(String chapterId);

    void saveChapter(ChapterMeta chapter);

    void saveSentences(String chapterId, List<Sentence> sentences);

    void updateTitle(String chapterId, String title);

    void deleteChapter(String chapterId);
}
