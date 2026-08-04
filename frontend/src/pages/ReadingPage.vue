<template>
  <div v-if="!isSettingsPageOpen" class="page-shell">
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
        <button class="ghost-button" @click="openSettingsPage">
          Settings
        </button>
      </div>
    </header>

    <main class="reading-list">
      <section
        v-for="paragraph in paragraphGroups"
        :key="`${chapter.chapterId}-${paragraph.id}`"
        class="paragraph-group"
      >
        <div class="paragraph-marker" aria-hidden="true">
          <span class="paragraph-label">P</span>
          <span v-if="showParagraphPosition" class="paragraph-position">
            {{ paragraph.position }}/{{ paragraph.total }}
          </span>
        </div>
        <div class="paragraph-sentences">
          <SentenceBlock
            v-for="sentence in paragraph.sentences"
            :key="`${chapter.chapterId}-${sentence.id}`"
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

  <main v-else class="settings-page">
    <header class="settings-page__header">
      <button class="ghost-button" type="button" @click="closeSettingsPage">Back</button>
      <div>
        <p class="eyebrow">MDReader</p>
        <h1>Settings</h1>
      </div>
    </header>

    <section class="settings-card">
      <label class="settings-field">
        <span>User</span>
        <input
          v-model="settingsUserInput"
          class="compact-input"
          autocomplete="off"
          @keyup.enter="applySettings"
        />
      </label>

      <label class="settings-field">
        <span>Document</span>
        <select
          v-model="settingsChapterId"
          class="compact-input"
          @change="syncSettingsExpectedParagraphCount"
        >
          <option
            v-for="availableChapter in chapters"
            :key="availableChapter.id"
            :value="availableChapter.id"
          >
            {{ availableChapter.title || availableChapter.id }}
          </option>
        </select>
      </label>

      <label class="settings-field">
        <span>Expected paragraphs</span>
        <input
          v-model="settingsExpectedParagraphCount"
          class="compact-input"
          type="number"
          min="1"
          placeholder="Disabled"
        />
        <small>Show Well Done! after this many paragraphs are completely read.</small>
      </label>

      <label class="settings-switch">
        <span>
          <strong>Paragraph position</strong>
          <small>Show the current paragraph and total, for example 23/50.</small>
        </span>
        <input v-model="settingsShowParagraphPosition" type="checkbox" role="switch" />
      </label>

      <div class="settings-actions">
        <button
          class="ghost-button ghost-button--danger"
          type="button"
          :disabled="!settingsChapterId || isDeletingChapter"
          @click="deleteSelectedDocument"
        >
          {{ isDeletingChapter ? 'Deleting...' : 'Delete document' }}
        </button>
        <button class="ghost-button ghost-button--primary" type="button" @click="applySettings">
          Apply
        </button>
      </div>
      <p v-if="settingsError" class="import-error">{{ settingsError }}</p>
    </section>
  </main>

  <div v-if="isWellDoneVisible" class="modal-backdrop" role="dialog" aria-modal="true">
    <section class="well-done-modal">
      <p class="eyebrow">Reading goal</p>
      <h2>Well Done!</h2>
      <p>You finished paragraph {{ wellDoneParagraphCount }}.</p>
      <div class="import-actions">
        <button class="ghost-button ghost-button--primary" type="button" @click="acknowledgeWellDone">
          Confirm
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import ScoreIndicator from '../components/ScoreIndicator.vue'
import SentenceBlock from '../components/SentenceBlock.vue'
import {
  deleteChapter,
  fetchChapter,
  fetchChapters,
  fetchProgress,
  importChapterMarkdown,
  saveProgress
} from '../api/readerApi'

const DEFAULT_CHAPTER_ID = 'chapter-1'
const DEFAULT_USER_ID = 'default'
const USER_STORAGE_KEY = 'mdreader.userId'
const PARAGRAPH_POSITION_STORAGE_KEY = 'mdreader.showParagraphPosition'
const EXPECTED_PARAGRAPH_STORAGE_PREFIX = 'mdreader.expectedParagraphs'
const WELL_DONE_ACK_STORAGE_PREFIX = 'mdreader.wellDoneAcknowledged'
const READ_TRIGGER_PERCENT = 15

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
const showParagraphPosition = ref(
  window.localStorage.getItem(PARAGRAPH_POSITION_STORAGE_KEY) === 'true'
)
const isSettingsPageOpen = ref(false)
const settingsUserInput = ref(activeUserId.value)
const settingsChapterId = ref(activeChapterId.value)
const settingsShowParagraphPosition = ref(showParagraphPosition.value)
const expectedParagraphCount = ref(0)
const settingsExpectedParagraphCount = ref('')
const isDeletingChapter = ref(false)
const settingsError = ref('')
const isWellDoneVisible = ref(false)
const wellDoneParagraphCount = ref(0)

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
const isWaitingForImportedDocumentScroll = ref(false)
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

  const entries = Array.from(groups.entries()).sort((a, b) => a[0] - b[0])
  return entries.map(([id, sentences], index) => ({
    id,
    sentences,
    position: index + 1,
    total: entries.length
  }))
})
const completedParagraphCount = computed(() => paragraphGroups.value.reduce((count, paragraph) => {
  const isComplete = paragraph.sentences.length > 0
    && paragraph.sentences.every(sentence => readIdSet.value.has(sentence.id))
  return isComplete ? count + 1 : count
}, 0))

function dedupe(ids) {
  return Array.from(new Set(ids)).sort((a, b) => a - b)
}

function normalizeChapterName(value) {
  return (value || '').trim()
}

function normalizeExpectedParagraphCount(value) {
  const count = Number.parseInt(value, 10)
  return Number.isFinite(count) && count > 0 ? count : 0
}

function paragraphSettingKey(prefix, userId, chapterId, target) {
  return [prefix, userId, chapterId, target]
    .map(value => encodeURIComponent(String(value || '')))
    .join('.')
}

function expectedParagraphStorageKey(userId = activeUserId.value, chapterId = activeChapterId.value) {
  return paragraphSettingKey(EXPECTED_PARAGRAPH_STORAGE_PREFIX, userId, chapterId, '')
}

function wellDoneAcknowledgementKey(target = expectedParagraphCount.value) {
  return paragraphSettingKey(
    WELL_DONE_ACK_STORAGE_PREFIX,
    activeUserId.value,
    activeChapterId.value,
    target
  )
}

function loadExpectedParagraphCount(chapterId = activeChapterId.value, userId = activeUserId.value) {
  const stored = window.localStorage.getItem(expectedParagraphStorageKey(userId, chapterId))
  expectedParagraphCount.value = normalizeExpectedParagraphCount(stored)
}

function maybeShowWellDone() {
  const target = expectedParagraphCount.value
  if (target <= 0 || isWellDoneVisible.value || completedParagraphCount.value < target) {
    return
  }
  if (window.localStorage.getItem(wellDoneAcknowledgementKey(target)) === 'true') {
    return
  }
  wellDoneParagraphCount.value = target
  isWellDoneVisible.value = true
}

function acknowledgeWellDone() {
  window.localStorage.setItem(wellDoneAcknowledgementKey(wellDoneParagraphCount.value), 'true')
  isWellDoneVisible.value = false
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

function createEmptyProgressForChapter(chapterId) {
  return {
    userId: activeUserId.value,
    chapterId,
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
  maybeShowWellDone()
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
  const chapterId = activeChapterId.value
  const userId = activeUserId.value
  const payload = createProgressPayload(chapterId, userId)
  persistTimer.value = window.setTimeout(() => {
    saveProgress(chapterId, userId, payload).catch((error) => {
      console.error('Failed to save progress', error)
    })
  }, 250)
}

function createProgressPayload(chapterId, userId, state = progress) {
  return {
    ...state,
    userId,
    chapterId,
    openedSentenceIds: [...state.openedSentenceIds],
    readSentenceIds: [...state.readSentenceIds],
    scoredSentenceIds: [...state.scoredSentenceIds],
    explanationUsedSentenceIds: [...state.explanationUsedSentenceIds]
  }
}

function markBottomVisibleSentencesAsRead() {
  if (isHydratingProgress.value || isWaitingForImportedDocumentScroll.value) {
    return
  }

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

async function hydrateChapterState(chapterResponse, progressResponse, finishHydration = true) {
  isHydratingProgress.value = true
  activeChapterId.value = chapterResponse.chapterId
  chapter.chapterId = chapterResponse.chapterId
  chapter.title = chapterResponse.title
  chapter.sentences = chapterResponse.sentences
  updateDocumentTitle()
  loadExpectedParagraphCount(chapterResponse.chapterId, activeUserId.value)
  mergeProgressState(progressResponse)
  progress.userId = activeUserId.value
  progress.chapterId = chapter.chapterId
  await nextTick()
  if (finishHydration) {
    isHydratingProgress.value = false
    maybeShowWellDone()
  }
}

async function loadChapterOptions() {
  chapters.value = await fetchChapters()
}

async function loadPage() {
  try {
    const chapterResponse = await fetchChapter(activeChapterId.value)
    const progressResponse = await fetchProgress(chapterResponse.chapterId, activeUserId.value)

    await hydrateChapterState(chapterResponse, progressResponse)
    updateUrlState(chapterResponse.chapterId)
    if (progress.lastSentenceId) {
      scrollToSentence(progress.lastSentenceId)
    } else {
      window.scrollTo({ top: 0, behavior: 'auto' })
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

function openSettingsPage() {
  settingsUserInput.value = activeUserId.value
  settingsChapterId.value = activeChapterId.value
  settingsShowParagraphPosition.value = showParagraphPosition.value
  settingsExpectedParagraphCount.value = expectedParagraphCount.value || ''
  settingsError.value = ''
  isSettingsPageOpen.value = true
}

function closeSettingsPage() {
  isSettingsPageOpen.value = false
}

function syncSettingsExpectedParagraphCount() {
  const userId = normalizeUserId(settingsUserInput.value)
  const stored = window.localStorage.getItem(
    expectedParagraphStorageKey(userId, settingsChapterId.value)
  )
  const count = normalizeExpectedParagraphCount(stored)
  settingsExpectedParagraphCount.value = count || ''
}

async function applySettings() {
  const nextUserId = normalizeUserId(settingsUserInput.value)
  const nextChapterId = settingsChapterId.value || activeChapterId.value
  const readingSourceChanged = nextUserId !== activeUserId.value
    || nextChapterId !== activeChapterId.value

  activeUserId.value = nextUserId
  userInput.value = nextUserId
  activeChapterId.value = nextChapterId
  showParagraphPosition.value = settingsShowParagraphPosition.value
  const nextExpectedParagraphCount = normalizeExpectedParagraphCount(
    settingsExpectedParagraphCount.value
  )
  expectedParagraphCount.value = nextExpectedParagraphCount
  window.localStorage.setItem(USER_STORAGE_KEY, nextUserId)
  window.localStorage.setItem(
    PARAGRAPH_POSITION_STORAGE_KEY,
    String(showParagraphPosition.value)
  )
  const expectedParagraphKey = expectedParagraphStorageKey(nextUserId, nextChapterId)
  if (nextExpectedParagraphCount > 0) {
    window.localStorage.setItem(expectedParagraphKey, String(nextExpectedParagraphCount))
  } else {
    window.localStorage.removeItem(expectedParagraphKey)
  }
  updateUrlState(nextChapterId)
  closeSettingsPage()

  if (readingSourceChanged) {
    await loadPage()
    refreshObservedSentences()
    markBottomVisibleSentencesAsRead()
  } else {
    maybeShowWellDone()
  }
}

async function deleteSelectedDocument() {
  const chapterToDelete = chapters.value.find(item => item.id === settingsChapterId.value)
  if (!chapterToDelete || isDeletingChapter.value) {
    return
  }
  const title = chapterToDelete.title || chapterToDelete.id
  if (!window.confirm(`Delete document "${title}"? This also removes its saved reading progress.`)) {
    return
  }

  isDeletingChapter.value = true
  settingsError.value = ''
  try {
    await deleteChapter(chapterToDelete.id)
    chapters.value = chapters.value.filter(item => item.id !== chapterToDelete.id)
    if (chapterToDelete.id !== activeChapterId.value) {
      settingsChapterId.value = activeChapterId.value
      return
    }

    const nextChapter = chapters.value[0]
    if (!nextChapter) {
      chapter.chapterId = ''
      chapter.title = ''
      chapter.sentences = []
      Object.assign(progress, createEmptyProgressForChapter(''))
      activeChapterId.value = ''
      updateDocumentTitle()
      closeSettingsPage()
      return
    }
    activeChapterId.value = nextChapter.id
    settingsChapterId.value = nextChapter.id
    updateUrlState(nextChapter.id)
    closeSettingsPage()
    await loadPage()
    refreshObservedSentences()
    markBottomVisibleSentencesAsRead()
  } catch (error) {
    settingsError.value = error.message || 'Delete failed'
  } finally {
    isDeletingChapter.value = false
  }
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
  isHydratingProgress.value = true
  isWaitingForImportedDocumentScroll.value = false
  window.clearTimeout(persistTimer.value)
  sentenceObserver.value?.disconnect()
  let importedChapter = false
  try {
    const previousChapterId = activeChapterId.value
    const previousUserId = activeUserId.value
    const previousProgress = createProgressPayload(previousChapterId, previousUserId)
    try {
      await saveProgress(previousChapterId, previousUserId, previousProgress)
    } catch (error) {
      console.error('Failed to save progress before import', error)
    }

    const chapterResponse = await importChapterMarkdown(null, {
      title: importForm.title,
      markdown: importForm.markdown
    })
    importedChapter = true
    const emptyProgress = createEmptyProgressForChapter(chapterResponse.chapterId)
    try {
      await saveProgress(
        chapterResponse.chapterId,
        activeUserId.value,
        createProgressPayload(chapterResponse.chapterId, activeUserId.value, emptyProgress)
      )
    } catch (error) {
      console.error('Failed to persist empty progress for imported document', error)
    }
    await hydrateChapterState(
      chapterResponse,
      emptyProgress,
      false
    )
    updateUrlState(chapterResponse.chapterId)
    isImportModalOpen.value = false
    isWaitingForImportedDocumentScroll.value = true
    await nextTick()
    window.scrollTo({ top: 0, behavior: 'auto' })
    refreshObservedSentences()
    isHydratingProgress.value = false
    await loadChapterOptions()
  } catch (error) {
    importError.value = error.message || 'Import failed'
    isHydratingProgress.value = false
    if (!importedChapter) {
      refreshObservedSentences()
    }
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
    if (isHydratingProgress.value) {
      return
    }
    if (isWaitingForImportedDocumentScroll.value) {
      if (window.scrollY <= 0) {
        return
      }
      isWaitingForImportedDocumentScroll.value = false
    }

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
