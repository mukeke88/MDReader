<template>
  <div class="page-shell">
    <ScoreIndicator
      :green-score="progress.greenScore"
      :red-score="progress.redScore"
      :manual-red-score="progress.manualRedScore"
      @update:greenScore="setGreenScore"
      @update:redScore="setRedScore"
      @update:manualRedScore="setManualRedScore"
    />
    <GlobalExplanationToggle
      :model-value="progress.globalExpanded"
      @update:modelValue="setGlobalExplanationEnabled"
    />

    <header class="page-header">
      <div>
        <p class="eyebrow">Shared Server</p>
        <h1>{{ chapter.title || 'Loading chapter...' }}</h1>
      </div>
      <div class="header-actions">
        <button
          v-if="progress.lastSentenceId"
          class="ghost-button"
          @click="scrollToSentence(progress.lastSentenceId)"
        >
          Back to Last Reading Position
        </button>
      </div>
    </header>

    <main class="reading-list">
      <section
        v-for="paragraph in paragraphGroups"
        :key="paragraph.id"
        class="paragraph-group"
      >
        <div class="paragraph-label" aria-hidden="true">P</div>
        <div class="paragraph-sentences">
          <SentenceBlock
            v-for="sentence in paragraph.sentences"
            :key="sentence.id"
            :sentence="sentence"
            :is-explanation-open="isExplanationOpen(sentence.id)"
            :is-read="readIdSet.has(sentence.id)"
            :observer="sentenceObserver"
            @toggle-explanation="toggleSentenceExplanation"
          />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import GlobalExplanationToggle from '../components/GlobalExplanationToggle.vue'
import ScoreIndicator from '../components/ScoreIndicator.vue'
import SentenceBlock from '../components/SentenceBlock.vue'
import { fetchChapter, fetchProgress, saveProgress } from '../api/readerApi'

const DEFAULT_CHAPTER_ID = 'chapter-1'
const READ_TRIGGER_PERCENT = 15
const TEMP_CHAPTER_NAME = 'temp'

function getChapterIdFromUrl() {
  const params = new URLSearchParams(window.location.search)
  return params.get('chapter') || DEFAULT_CHAPTER_ID
}

const activeChapterId = ref(getChapterIdFromUrl())

const chapter = reactive({
  chapterId: activeChapterId.value,
  title: '',
  sentences: []
})

const progress = reactive({
  chapterId: activeChapterId.value,
  lastSentenceId: null,
  totalScore: 0,
  greenScore: 0,
  redScore: 0,
  manualRedScore: 0,
  globalExpanded: false,
  openedSentenceIds: [],
  readSentenceIds: [],
  scoredSentenceIds: [],
  explanationUsedSentenceIds: []
})

const sentenceObserver = ref(null)
const persistTimer = ref(null)
const isHydratingProgress = ref(true)

const readIdSet = computed(() => new Set(progress.readSentenceIds))
const openedIdSet = computed(() => new Set(progress.openedSentenceIds))
const scoredIdSet = computed(() => new Set(progress.scoredSentenceIds))
const explanationUsedIdSet = computed(() => new Set(progress.explanationUsedSentenceIds))
const orderedSentenceIds = computed(() => chapter.sentences.map(sentence => sentence.id))
const paragraphGroups = computed(() => {
  const groups = new Map()
  chapter.sentences.forEach((sentence) => {
    const paragraphId = sentence.paragraphId || 1
    if (!groups.has(paragraphId)) {
      groups.set(paragraphId, [])
    }
    groups.get(paragraphId).push(sentence)
  })

  return Array.from(groups.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([id, sentences]) => ({ id, sentences }))
})

function dedupe(ids) {
  return Array.from(new Set(ids)).sort((a, b) => a - b)
}

function normalizeChapterName(value) {
  return (value || '').trim()
}

function buildProgressKey(title) {
  const chapterName = normalizeChapterName(title)
  if (!chapterName) {
    return activeChapterId.value
  }

  if (chapterName.toLowerCase() === TEMP_CHAPTER_NAME) {
    return null
  }

  return chapterName.replace(/[\\/#?%]/g, '_')
}

function getCurrentProgressKey() {
  return buildProgressKey(chapter.title)
}

function updateDocumentTitle() {
  const chapterName = normalizeChapterName(chapter.title)
  document.title = chapterName || 'MDReader'
}

function deriveGreenScore(scoredIds, explanationUsedIds) {
  return scoredIds.filter(id => !explanationUsedIds.includes(id)).length
}

function deriveRedScore(scoredIds, explanationUsedIds) {
  return scoredIds.filter(id => explanationUsedIds.includes(id)).length
}

function mergeProgressState(savedProgress) {
  const scoredSentenceIds = dedupe(savedProgress.scoredSentenceIds || [])
  const explanationUsedSentenceIds = dedupe(savedProgress.explanationUsedSentenceIds || [])

  progress.lastSentenceId = savedProgress.lastSentenceId || null
  progress.totalScore = savedProgress.totalScore || 0
  progress.greenScore = Number.isFinite(savedProgress.greenScore)
    ? savedProgress.greenScore
    : deriveGreenScore(scoredSentenceIds, explanationUsedSentenceIds)
  progress.redScore = Number.isFinite(savedProgress.redScore)
    ? savedProgress.redScore
    : deriveRedScore(scoredSentenceIds, explanationUsedSentenceIds)
  progress.manualRedScore = Number.isFinite(savedProgress.manualRedScore)
    ? savedProgress.manualRedScore
    : 0
  progress.globalExpanded = !!savedProgress.globalExpanded
  progress.openedSentenceIds = dedupe(savedProgress.openedSentenceIds || [])
  progress.readSentenceIds = dedupe(savedProgress.readSentenceIds || [])
  progress.scoredSentenceIds = scoredSentenceIds
  progress.explanationUsedSentenceIds = explanationUsedSentenceIds
}

function createEmptyProgress() {
  return {
    chapterId: getCurrentProgressKey() || activeChapterId.value,
    lastSentenceId: null,
    totalScore: 0,
    greenScore: 0,
    redScore: 0,
    manualRedScore: 0,
    globalExpanded: false,
    openedSentenceIds: [],
    readSentenceIds: [],
    scoredSentenceIds: [],
    explanationUsedSentenceIds: []
  }
}

function isExplanationOpen(sentenceId) {
  return progress.globalExpanded || openedIdSet.value.has(sentenceId)
}

function markExplanationUsed(sentenceId) {
  if (explanationUsedIdSet.value.has(sentenceId)) {
    return
  }

  progress.explanationUsedSentenceIds = dedupe(progress.explanationUsedSentenceIds.concat(sentenceId))

  if (scoredIdSet.value.has(sentenceId)) {
    progress.greenScore = Math.max(0, progress.greenScore - 1)
    progress.redScore += 1
  }
}

function clearExplanationUsed(sentenceId) {
  if (!explanationUsedIdSet.value.has(sentenceId)) {
    return
  }

  progress.explanationUsedSentenceIds = progress.explanationUsedSentenceIds.filter(id => id !== sentenceId)

  if (scoredIdSet.value.has(sentenceId)) {
    progress.redScore = Math.max(0, progress.redScore - 1)
    progress.greenScore += 1
  }
}

function toggleSentenceExplanation(sentenceId) {
  if (openedIdSet.value.has(sentenceId)) {
    clearExplanationUsed(sentenceId)
    progress.openedSentenceIds = progress.openedSentenceIds.filter(id => id !== sentenceId)
    return
  }

  markExplanationUsed(sentenceId)
  handleSentenceRead(sentenceId)
  progress.openedSentenceIds = dedupe(progress.openedSentenceIds.concat(sentenceId))
}

function setGlobalExplanationEnabled(enabled) {
  progress.globalExpanded = enabled
  if (!enabled) {
    return
  }

  const unscoredIds = chapter.sentences
    .map(sentence => sentence.id)
    .filter(id => !scoredIdSet.value.has(id))
  progress.explanationUsedSentenceIds = dedupe(progress.explanationUsedSentenceIds.concat(unscoredIds))
}

function setGreenScore(value) {
  progress.greenScore = Math.max(0, Number.parseInt(value, 10) || 0)
}

function setRedScore(value) {
  progress.redScore = Math.max(0, Number.parseInt(value, 10) || 0)
}

function setManualRedScore(value) {
  progress.manualRedScore = Math.max(0, Number.parseInt(value, 10) || 0)
}

function scoreSentence(sentenceId) {
  if (scoredIdSet.value.has(sentenceId)) {
    return
  }

  if (explanationUsedIdSet.value.has(sentenceId)) {
    progress.redScore += 1
  } else {
    progress.greenScore += 1
  }

  progress.scoredSentenceIds = dedupe(progress.scoredSentenceIds.concat(sentenceId))
}

function handleSentenceRead(sentenceId) {
  if (!readIdSet.value.has(sentenceId)) {
    progress.readSentenceIds = dedupe(progress.readSentenceIds.concat(sentenceId))
  }
  progress.lastSentenceId = sentenceId
  scoreSentence(sentenceId)
}

function markSentencesUpTo(targetSentenceId) {
  for (const sentenceId of orderedSentenceIds.value) {
    if (sentenceId > targetSentenceId) {
      break
    }

    handleSentenceRead(sentenceId)
  }
}

function scrollToSentence(sentenceId) {
  nextTick(() => {
    const target = document.getElementById(`sentence-${sentenceId}`)
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  })
}

function schedulePersist() {
  if (isHydratingProgress.value) {
    return
  }

  const progressKey = getCurrentProgressKey()
  if (!progressKey) {
    return
  }

  window.clearTimeout(persistTimer.value)
  persistTimer.value = window.setTimeout(() => {
    saveProgress(progressKey, { ...progress, chapterId: progressKey }).catch((error) => {
      console.error('Failed to save progress', error)
    })
  }, 250)
}

function markBottomVisibleSentencesAsRead() {
  const nearBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 24
  if (!nearBottom) {
    return
  }

  document.querySelectorAll('[data-sentence-id]').forEach((element) => {
    const sentenceId = Number(element.dataset.sentenceId)
    const rect = element.getBoundingClientRect()
    const visible = rect.top < window.innerHeight && rect.bottom > 0

    if (visible && !Number.isNaN(sentenceId)) {
      handleSentenceRead(sentenceId)
    }
  })
}

function refreshObservedSentences() {
  if (!sentenceObserver.value) {
    return
  }

  sentenceObserver.value.disconnect()
  document.querySelectorAll('[data-sentence-id]').forEach((element) => {
    sentenceObserver.value.observe(element)
  })
}

async function hydrateChapterState(chapterResponse, progressResponse) {
  isHydratingProgress.value = true
  activeChapterId.value = chapterResponse.chapterId
  chapter.chapterId = chapterResponse.chapterId
  chapter.title = chapterResponse.title
  chapter.sentences = chapterResponse.sentences
  updateDocumentTitle()
  mergeProgressState(progressResponse)
  progress.chapterId = getCurrentProgressKey() || chapter.chapterId
  await nextTick()
  isHydratingProgress.value = false
}

async function loadPage() {
  try {
    const chapterResponse = await fetchChapter(activeChapterId.value)
    const progressKey = buildProgressKey(chapterResponse.title)
    const progressResponse = progressKey
      ? await fetchProgress(progressKey)
      : createEmptyProgress()

    await hydrateChapterState(chapterResponse, progressResponse)
    updateUrlChapter(chapterResponse.chapterId)
    if (progress.lastSentenceId) {
      scrollToSentence(progress.lastSentenceId)
    }
  } catch (error) {
    if (activeChapterId.value !== DEFAULT_CHAPTER_ID) {
      activeChapterId.value = DEFAULT_CHAPTER_ID
      updateUrlChapter(DEFAULT_CHAPTER_ID)
      await loadPage()
      return
    }

    console.error('Failed to load chapter', error)
  }
}

function updateUrlChapter(chapterId) {
  const url = new URL(window.location.href)
  if (chapterId === DEFAULT_CHAPTER_ID) {
    url.searchParams.delete('chapter')
  } else {
    url.searchParams.set('chapter', chapterId)
  }
  window.history.replaceState({}, '', url)
}

function buildObserver() {
  const bottomMargin = -(100 - READ_TRIGGER_PERCENT)
  sentenceObserver.value = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) {
        return
      }

      const sentenceId = Number(entry.target.dataset.sentenceId)
      if (!Number.isNaN(sentenceId)) {
        markSentencesUpTo(sentenceId)
      }
    })
  }, {
    root: null,
    threshold: 0,
    rootMargin: `0px 0px ${bottomMargin}% 0px`
  })
}

watch(progress, () => {
  schedulePersist()
}, { deep: true })

watch(() => [progress.greenScore, progress.redScore, progress.manualRedScore], ([greenScore, redScore, manualRedScore]) => {
  progress.totalScore = greenScore + redScore + manualRedScore
}, { immediate: true })

onMounted(async () => {
  updateDocumentTitle()
  buildObserver()
  window.addEventListener('scroll', markBottomVisibleSentencesAsRead, { passive: true })
  await loadPage()
  refreshObservedSentences()
  markBottomVisibleSentencesAsRead()
})

onBeforeUnmount(() => {
  if (sentenceObserver.value) {
    sentenceObserver.value.disconnect()
  }
  window.removeEventListener('scroll', markBottomVisibleSentencesAsRead)
  window.clearTimeout(persistTimer.value)
})
</script>
