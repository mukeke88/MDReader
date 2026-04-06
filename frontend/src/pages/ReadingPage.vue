<template>
  <div class="page-shell">
    <ScoreIndicator
      :green-score="progress.greenScore"
      :red-score="progress.redScore"
      @update:greenScore="setGreenScore"
      @update:redScore="setRedScore"
    />
    <GlobalExplanationToggle
      :model-value="progress.globalExpanded"
      @update:modelValue="setGlobalExplanationEnabled"
    />

    <div class="read-zone-overlay" aria-hidden="true">
      <div class="read-zone-overlay__active" :style="readZoneStyle">
        <span class="read-zone-overlay__label">Read trigger zone: top {{ READ_TRIGGER_PERCENT }}%</span>
      </div>
      <div class="read-zone-overlay__inactive"></div>
    </div>
    <header class="page-header">
      <div>
        <p class="eyebrow">Local MVP</p>
        <h1>{{ chapter.title || 'Loading chapter...' }}</h1>
      </div>
      <div class="header-actions">
        <button class="ghost-button" @click="openImportModal">Paste Markdown</button>
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
        <div class="paragraph-label">PARAGRAPH {{ paragraph.id }}</div>
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

    <div v-if="isImportModalOpen" class="modal-backdrop" @click.self="closeImportModal">
      <section class="import-modal">
        <div class="import-modal__header">
          <div>
            <p class="eyebrow">Import</p>
            <h2>Paste Markdown</h2>
          </div>
          <button class="ghost-button" @click="closeImportModal">Close</button>
        </div>

        <label class="field-label" for="import-title">Title</label>
        <input id="import-title" v-model="importForm.title" class="text-input" type="text" placeholder="Optional title override" />

        <label class="field-label" for="import-markdown">Markdown Content</label>
        <textarea
          id="import-markdown"
          v-model="importForm.markdown"
          class="markdown-input"
          placeholder="Paste markdown here"
        />

        <p v-if="importError" class="import-error">{{ importError }}</p>

        <div class="import-actions">
          <button class="ghost-button" @click="closeImportModal">Cancel</button>
          <button class="toggle-button" :disabled="isImporting" @click="submitImport">
            {{ isImporting ? 'Importing...' : 'Load as Current Material' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import GlobalExplanationToggle from '../components/GlobalExplanationToggle.vue'
import ScoreIndicator from '../components/ScoreIndicator.vue'
import SentenceBlock from '../components/SentenceBlock.vue'
import { fetchChapter, fetchProgress, importChapter, saveProgress } from '../api/readerApi'

const chapterId = 'chapter-1'
const READ_TRIGGER_PERCENT = 15

const chapter = reactive({
  chapterId,
  title: '',
  sentences: []
})

const progress = reactive({
  chapterId,
  lastSentenceId: null,
  totalScore: 0,
  greenScore: 0,
  redScore: 0,
  globalExpanded: false,
  openedSentenceIds: [],
  readSentenceIds: [],
  scoredSentenceIds: [],
  explanationUsedSentenceIds: []
})

const sentenceObserver = ref(null)
const persistTimer = ref(null)
const isHydratingProgress = ref(true)
const isImportModalOpen = ref(false)
const isImporting = ref(false)
const importError = ref('')
const importForm = reactive({
  title: '',
  markdown: ''
})

const readIdSet = computed(() => new Set(progress.readSentenceIds))
const openedIdSet = computed(() => new Set(progress.openedSentenceIds))
const scoredIdSet = computed(() => new Set(progress.scoredSentenceIds))
const explanationUsedIdSet = computed(() => new Set(progress.explanationUsedSentenceIds))
const orderedSentenceIds = computed(() => chapter.sentences.map(sentence => sentence.id))
const readZoneStyle = computed(() => ({
  height: `${READ_TRIGGER_PERCENT}vh`
}))

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
  progress.globalExpanded = !!savedProgress.globalExpanded
  progress.openedSentenceIds = dedupe(savedProgress.openedSentenceIds || [])
  progress.readSentenceIds = dedupe(savedProgress.readSentenceIds || [])
  progress.scoredSentenceIds = scoredSentenceIds
  progress.explanationUsedSentenceIds = explanationUsedSentenceIds
}

function createEmptyProgress() {
  return {
    chapterId,
    lastSentenceId: null,
    totalScore: 0,
    greenScore: 0,
    redScore: 0,
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

  window.clearTimeout(persistTimer.value)
  persistTimer.value = window.setTimeout(() => {
    saveProgress(chapterId, { ...progress }).catch((error) => {
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

function findSentenceElementFromSelection() {
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
    return null
  }

  let node = selection.anchorNode
  if (!node) {
    return null
  }

  if (node.nodeType === Node.TEXT_NODE) {
    node = node.parentElement
  }

  return node instanceof Element ? node.closest('[data-sentence-id]') : null
}

function syncReadProgressFromSelection() {
  const sentenceElement = findSentenceElementFromSelection()
  if (!sentenceElement) {
    return
  }

  const sentenceId = Number(sentenceElement.dataset.sentenceId)
  if (!Number.isNaN(sentenceId)) {
    markSentencesUpTo(sentenceId)
  }
}

async function hydrateChapterState(chapterResponse, progressResponse) {
  isHydratingProgress.value = true
  chapter.chapterId = chapterResponse.chapterId
  chapter.title = chapterResponse.title
  chapter.sentences = chapterResponse.sentences
  mergeProgressState(progressResponse)
  await nextTick()
  isHydratingProgress.value = false
}

async function loadPage() {
  const [chapterResponse, progressResponse] = await Promise.all([
    fetchChapter(chapterId),
    fetchProgress(chapterId)
  ])

  await hydrateChapterState(chapterResponse, progressResponse)
  if (progress.lastSentenceId) {
    scrollToSentence(progress.lastSentenceId)
  }
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

function openImportModal() {
  importError.value = ''
  isImportModalOpen.value = true
}

function closeImportModal() {
  if (isImporting.value) {
    return
  }
  isImportModalOpen.value = false
  importError.value = ''
}

async function submitImport() {
  if (!importForm.markdown.trim()) {
    importError.value = 'Paste markdown content first.'
    return
  }

  isImporting.value = true
  importError.value = ''

  try {
    const chapterResponse = await importChapter(chapterId, {
      title: importForm.title.trim(),
      markdown: importForm.markdown
    })

    await hydrateChapterState(chapterResponse, createEmptyProgress())
    importForm.title = ''
    importForm.markdown = ''
    closeImportModal()
    window.scrollTo({ top: 0, behavior: 'smooth' })
    markBottomVisibleSentencesAsRead()
  } catch (error) {
    importError.value = 'Import failed. Check the pasted markdown format and try again.'
    console.error('Failed to import chapter markdown', error)
  } finally {
    isImporting.value = false
  }
}

watch(progress, () => {
  schedulePersist()
}, { deep: true })

watch(() => [progress.greenScore, progress.redScore], ([greenScore, redScore]) => {
  progress.totalScore = greenScore + redScore
}, { immediate: true })

onMounted(async () => {
  buildObserver()
  window.addEventListener('scroll', markBottomVisibleSentencesAsRead, { passive: true })
  document.addEventListener('selectionchange', syncReadProgressFromSelection)
  await loadPage()
  refreshObservedSentences()
  markBottomVisibleSentencesAsRead()
})

onBeforeUnmount(() => {
  if (sentenceObserver.value) {
    sentenceObserver.value.disconnect()
  }
  window.removeEventListener('scroll', markBottomVisibleSentencesAsRead)
  document.removeEventListener('selectionchange', syncReadProgressFromSelection)
  window.clearTimeout(persistTimer.value)
})
</script>
