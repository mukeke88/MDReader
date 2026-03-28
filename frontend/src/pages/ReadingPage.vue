<template>
  <div class="page-shell">
    <header class="page-header">
      <div>
        <p class="eyebrow">Local MVP</p>
        <h1>{{ chapter.title || 'Loading chapter...' }}</h1>
      </div>
      <ScoreIndicator :score="progress.totalScore" />
    </header>

    <section class="toolbar">
      <GlobalExplanationToggle @expand-all="expandAll" @collapse-all="collapseAll" />
      <button
        v-if="progress.lastSentenceId"
        class="ghost-button"
        @click="scrollToSentence(progress.lastSentenceId)"
      >
        Back to Last Reading Position
      </button>
    </section>

    <main class="reading-list">
      <SentenceBlock
        v-for="sentence in chapter.sentences"
        :key="sentence.id"
        :sentence="sentence"
        :is-explanation-open="isExplanationOpen(sentence.id)"
        :is-read="readIdSet.has(sentence.id)"
        :observer="sentenceObserver"
        @toggle-explanation="toggleSentenceExplanation"
      />
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import GlobalExplanationToggle from '../components/GlobalExplanationToggle.vue'
import ScoreIndicator from '../components/ScoreIndicator.vue'
import SentenceBlock from '../components/SentenceBlock.vue'
import { fetchChapter, fetchProgress, saveProgress } from '../api/readerApi'

const chapterId = 'chapter-1'

const chapter = reactive({
  chapterId,
  title: '',
  sentences: []
})

const progress = reactive({
  chapterId,
  lastSentenceId: null,
  totalScore: 0,
  globalExpanded: false,
  openedSentenceIds: [],
  readSentenceIds: [],
  scoredSentenceIds: [],
  explanationUsedSentenceIds: []
})

const sentenceObserver = ref(null)
const persistTimer = ref(null)
const readIdSet = computed(() => new Set(progress.readSentenceIds))
const openedIdSet = computed(() => new Set(progress.openedSentenceIds))
const scoredIdSet = computed(() => new Set(progress.scoredSentenceIds))
const explanationUsedIdSet = computed(() => new Set(progress.explanationUsedSentenceIds))

function dedupe(ids) {
  return Array.from(new Set(ids)).sort((a, b) => a - b)
}

function mergeProgressState(savedProgress) {
  progress.lastSentenceId = savedProgress.lastSentenceId || null
  progress.totalScore = savedProgress.totalScore || 0
  progress.globalExpanded = !!savedProgress.globalExpanded
  progress.openedSentenceIds = dedupe(savedProgress.openedSentenceIds || [])
  progress.readSentenceIds = dedupe(savedProgress.readSentenceIds || [])
  progress.scoredSentenceIds = dedupe(savedProgress.scoredSentenceIds || [])
  progress.explanationUsedSentenceIds = dedupe(savedProgress.explanationUsedSentenceIds || [])
}

function isExplanationOpen(sentenceId) {
  return progress.globalExpanded || openedIdSet.value.has(sentenceId)
}

function markExplanationUsed(sentenceId) {
  if (scoredIdSet.value.has(sentenceId) || explanationUsedIdSet.value.has(sentenceId)) {
    return
  }
  progress.explanationUsedSentenceIds = dedupe(progress.explanationUsedSentenceIds.concat(sentenceId))
}

function toggleSentenceExplanation(sentenceId) {
  if (openedIdSet.value.has(sentenceId)) {
    progress.openedSentenceIds = progress.openedSentenceIds.filter(id => id !== sentenceId)
    return
  }

  markExplanationUsed(sentenceId)
  progress.openedSentenceIds = dedupe(progress.openedSentenceIds.concat(sentenceId))
}

function expandAll() {
  progress.globalExpanded = true
  const unscoredIds = chapter.sentences
    .map(sentence => sentence.id)
    .filter(id => !scoredIdSet.value.has(id))
  progress.explanationUsedSentenceIds = dedupe(progress.explanationUsedSentenceIds.concat(unscoredIds))
}

function collapseAll() {
  progress.globalExpanded = false
}

function scoreSentence(sentenceId) {
  if (scoredIdSet.value.has(sentenceId)) {
    return
  }

  progress.totalScore += explanationUsedIdSet.value.has(sentenceId) ? -1 : 1
  progress.scoredSentenceIds = dedupe(progress.scoredSentenceIds.concat(sentenceId))
}

function handleSentenceRead(sentenceId) {
  if (!readIdSet.value.has(sentenceId)) {
    progress.readSentenceIds = dedupe(progress.readSentenceIds.concat(sentenceId))
  }
  progress.lastSentenceId = sentenceId
  scoreSentence(sentenceId)
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
  window.clearTimeout(persistTimer.value)
  persistTimer.value = window.setTimeout(() => {
    saveProgress(chapterId, { ...progress }).catch((error) => {
      console.error('Failed to save progress', error)
    })
  }, 250)
}

async function loadPage() {
  const [chapterResponse, progressResponse] = await Promise.all([
    fetchChapter(chapterId),
    fetchProgress(chapterId)
  ])

  chapter.chapterId = chapterResponse.chapterId
  chapter.title = chapterResponse.title
  chapter.sentences = chapterResponse.sentences
  mergeProgressState(progressResponse)

  await nextTick()
  if (progress.lastSentenceId) {
    scrollToSentence(progress.lastSentenceId)
  }
}

function buildObserver() {
  sentenceObserver.value = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) {
        return
      }

      const sentenceId = Number(entry.target.dataset.sentenceId)
      if (!Number.isNaN(sentenceId)) {
        handleSentenceRead(sentenceId)
      }
    })
  }, {
    root: null,
    threshold: 0,
    rootMargin: '0px 0px -55% 0px'
  })
}

watch(progress, () => {
  schedulePersist()
}, { deep: true })

onMounted(async () => {
  buildObserver()
  await loadPage()
})

onBeforeUnmount(() => {
  if (sentenceObserver.value) {
    sentenceObserver.value.disconnect()
  }
  window.clearTimeout(persistTimer.value)
})
</script>

