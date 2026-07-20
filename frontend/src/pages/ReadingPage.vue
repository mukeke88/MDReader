<template>
  <div class="page-shell">
    <ScoreIndicator
      :green-score="progress.greenScore"
      :red-score="progress.redScore"
      @update:greenScore="setGreenScore"
      @update:redScore="setRedScore"
    />

    <header class="page-header">
      <div>
        <p class="eyebrow">Reader</p>
        <h1>{{ chapter.title || 'Loading chapter...' }}</h1>
      </div>
      <div class="reader-controls">
        <label class="compact-field">
          User
          <input
            v-model="userInput"
            class="compact-input"
            autocomplete="off"
            @change="applyUserInput"
            @keyup.enter="applyUserInput"
          />
        </label>
        <label class="compact-field">
          Document
          <select class="compact-input" :value="activeChapterId" @change="changeChapter">
            <option
              v-for="availableChapter in chapters"
              :key="availableChapter.id"
              :value="availableChapter.id"
            >
              {{ availableChapter.title || availableChapter.id }}
            </option>
          </select>
        </label>
      </div>
      <div class="header-actions">
        <button
          v-if="progress.lastSentenceId"
          class="ghost-button"
          @click="scrollToSentence(progress.lastSentenceId)"
        >
          Back to Last Reading Position
        </button>
        <button class="ghost-button ghost-button--primary" @click="openImportModal">
          Import Markdown
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

    <div v-if="isImportModalOpen" class="modal-backdrop" @click.self="closeImportModal">
      <form class="import-modal" @submit.prevent="submitMarkdownImport">
        <div class="import-modal__header">
          <div>
            <p class="eyebrow">Markdown Import</p>
            <h2>Import Chapter</h2>
          </div>
          <button type="button" class="ghost-button" @click="closeImportModal">Close</button>
        </div>

        <label class="field-label">
          Chapter Title
          <input v-model="importForm.title" class="text-input" required />
        </label>

        <label class="field-label">
          Markdown File
          <input
            class="text-input"
            type="file"
            accept=".md,.markdown,.txt,text/markdown,text/plain"
            @change="loadMarkdownFile"
          />
        </label>

        <label class="field-label">
          Markdown
          <textarea
            v-model="importForm.markdown"
            class="markdown-input"
            required
            placeholder="PARAGRAPH&#10;**Sentence text**&#10;Explanation text"
          />
        </label>

        <p v-if="importError" class="import-error">{{ importError }}</p>

        <div class="import-actions">
          <button type="button" class="ghost-button" @click="closeImportModal">Cancel</button>
          <button type="submit" class="ghost-button ghost-button--primary" :disabled="isImporting">
            {{ isImporting ? 'Importing...' : 'Import' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import ScoreIndicator from '../components/ScoreIndicator.vue'
import SentenceBlock from '../components/SentenceBlock.vue'
import { fetchChapter, fetchChapters, fetchProgress, importChapterMarkdown, saveProgress } from '../api/readerApi'

const DEFAULT_CHAPTER_ID = 'chapter-1'
const DEFAULT_USER_ID = 'default'
const USER_STORAGE_KEY = 'mdreader.userId'
const READ_TRIGGER_PERCENT = 15
const TEMP_CHAPTER_NAME = 'temp'

function getChapterIdFromUrl() {
  const params = new URLSearchParams(window.location.search)
  return params.get('chapter') || DEFAULT_CHAPTER_ID
}

function normalizeUserId(value) {
  return (value || '').trim() || DEFAULT_USER_ID
}

function getUserIdFromUrl() {
  const params = new URLSearchParams(window.location.search)
  return params.get('user') || window.localStorage.getItem(USER_STORAGE_KEY) || DEFAULT_USER_ID
}

const activeChapterId = ref(getChapterIdFromUrl())
const activeUserId = ref(normalizeUserId(getUserIdFromUrl()))
const userInput = ref(activeUserId.value)
const chapters = ref([])

const chapter = reactive({
  chapterId: activeChapterId.value,
  title: '',
  sentences: []
})

const progress = reactive({
  userId: activeUserId.value,
  chapterId: activeChapterId.value,
  lastSentenceId: null,
  totalScore: 0,
  greenScore: 0,
  redScore: 0,
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

function buildLegacyProgressKey(title) {
  const chapterName = normalizeChapterName(title)
  if (!chapterName || chapterName.toLowerCase() === TEMP_CHAPTER_NAME) {
    return null
  }

  return chapterName.replace(/[\\/#?%]/g, '_')
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

  progress.userId = savedProgress.userId || activeUserId.value
  progress.chapterId = savedProgress.chapterId || activeChapterId.value
  progress.lastSentenceId = savedProgress.lastSentenceId || null
  progress.totalScore = savedProgress.totalScore || 0
  progress.greenScore = Number.isFinite(savedProgress.greenScore)
    ? savedProgress.greenScore
    : deriveGreenScore(scoredSentenceIds, explanationUsedSentenceIds)
  progress.redScore = Number.isFinite(savedProgress.redScore)
    ? savedProgress.redScore
    : deriveRedScore(scoredSentenceIds, explanationUsedSentenceIds)
  progress.openedSentenceIds = dedupe(savedProgress.openedSentenceIds || [])
  progress.readSentenceIds = dedupe(savedProgress.readSentenceIds || [])
  progress.scoredSentenceIds = scoredSentenceIds
  progress.explanationUsedSentenceIds = explanationUsedSentenceIds
}

function createEmptyProgress() {
  return {
    userId: activeUserId.value,
    chapterId: activeChapterId.value,
    lastSentenceId: null,
    totalScore: 0,
    greenScore: 0,
    redScore: 0,
    openedSentenceIds: [],
    readSentenceIds: [],
    scoredSentenceIds: [],
    explanationUsedSentenceIds: []
  }
}

function hasSavedProgress(savedProgress) {
  return Boolean(
    savedProgress.lastSentenceId ||
    savedProgress.totalScore ||
    savedProgress.greenScore ||
    savedProgress.redScore ||
    savedProgress.manualRedScore ||
    (savedProgress.openedSentenceIds || []).length ||
    (savedProgress.readSentenceIds || []).length ||
    (savedProgress.scoredSentenceIds || []).length ||
    (savedProgress.explanationUsedSentenceIds || []).length
  )
}

function isExplanationOpen(sentenceId) {
  return openedIdSet.value.has(sentenceId)
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
  if (!progress.lastSentenceId || sentenceId > progress.lastSentenceId) {
    progress.lastSentenceId = sentenceId
  }
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
    const payload = {
      ...progress,
      userId: activeUserId.value,
      chapterId: activeChapterId.value
    }
    saveProgress(activeChapterId.value, activeUserId.value, payload).catch((error) => {
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
  progress.userId = activeUserId.value
  progress.chapterId = chapter.chapterId
  await nextTick()
  isHydratingProgress.value = false
}

async function loadChapterOptions() {
  chapters.value = await fetchChapters()
}

async function loadPage() {
  try {
    const chapterResponse = await fetchChapter(activeChapterId.value)
    let progressResponse = await fetchProgress(chapterResponse.chapterId, activeUserId.value)
    const legacyProgressKey = buildLegacyProgressKey(chapterResponse.title)
    if (!hasSavedProgress(progressResponse) && legacyProgressKey && legacyProgressKey !== chapterResponse.chapterId) {
      progressResponse = await fetchProgress(legacyProgressKey, activeUserId.value)
    }

    await hydrateChapterState(chapterResponse, progressResponse)
    updateUrlState(chapterResponse.chapterId)
    if (progress.lastSentenceId) {
      scrollToSentence(progress.lastSentenceId)
    }
  } catch (error) {
    if (activeChapterId.value !== DEFAULT_CHAPTER_ID) {
      activeChapterId.value = DEFAULT_CHAPTER_ID
      updateUrlState(DEFAULT_CHAPTER_ID)
      await loadPage()
      return
    }

    console.error('Failed to load chapter', error)
  }
}

async function applyUserInput() {
  const nextUserId = normalizeUserId(userInput.value)
  if (nextUserId === activeUserId.value) {
    userInput.value = nextUserId
    return
  }

  activeUserId.value = nextUserId
  userInput.value = nextUserId
  window.localStorage.setItem(USER_STORAGE_KEY, nextUserId)
  updateUrlState(activeChapterId.value)
  await loadPage()
}

async function changeChapter(event) {
  const nextChapterId = event.target.value
  if (!nextChapterId || nextChapterId === activeChapterId.value) {
    return
  }

  activeChapterId.value = nextChapterId
  updateUrlState(nextChapterId)
  await loadPage()
  refreshObservedSentences()
  markBottomVisibleSentencesAsRead()
}

function openImportModal() {
  importForm.title = normalizeChapterName(chapter.title)
  importForm.markdown = ''
  importError.value = ''
  isImportModalOpen.value = true
}

function closeImportModal() {
  if (isImporting.value) {
    return
  }
  isImportModalOpen.value = false
}

function titleFromFileName(name) {
  return name.replace(/\.(md|markdown|txt)$/i, '')
}

function loadMarkdownFile(event) {
  const [file] = event.target.files || []
  if (!file) {
    return
  }

  if (!normalizeChapterName(importForm.title)) {
    importForm.title = titleFromFileName(file.name)
  }

  const reader = new FileReader()
  reader.onload = () => {
    importForm.markdown = String(reader.result || '')
  }
  reader.onerror = () => {
    importError.value = 'Unable to read selected file'
  }
  reader.readAsText(file)
}

async function submitMarkdownImport() {
  isImporting.value = true
  importError.value = ''
  try {
    const chapterResponse = await importChapterMarkdown(activeChapterId.value, {
      title: importForm.title,
      markdown: importForm.markdown
    })
    await hydrateChapterState(chapterResponse, createEmptyProgress())
    await loadChapterOptions()
    refreshObservedSentences()
    updateUrlState(chapterResponse.chapterId)
    isImportModalOpen.value = false
  } catch (error) {
    importError.value = error.message || 'Import failed'
  } finally {
    isImporting.value = false
  }
}

function updateUrlState(chapterId) {
  const url = new URL(window.location.href)
  if (chapterId === DEFAULT_CHAPTER_ID) {
    url.searchParams.delete('chapter')
  } else {
    url.searchParams.set('chapter', chapterId)
  }
  if (activeUserId.value === DEFAULT_USER_ID) {
    url.searchParams.delete('user')
  } else {
    url.searchParams.set('user', activeUserId.value)
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

watch(() => [progress.greenScore, progress.redScore], ([greenScore, redScore]) => {
  progress.totalScore = greenScore + redScore
}, { immediate: true })

onMounted(async () => {
  updateDocumentTitle()
  buildObserver()
  window.addEventListener('scroll', markBottomVisibleSentencesAsRead, { passive: true })
  await loadChapterOptions()
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
