const configuredApiBase = window.MDREADER_CONFIG?.apiBaseUrl || '/api'
const API_BASE = configuredApiBase.replace(/\/+$/, '')

function encodePathSegment(value) {
  return encodeURIComponent(value)
}

async function handleResponse(response) {
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`)
  }
  if (response.status === 204) {
    return null
  }
  const body = await response.text()
  return body.trim() ? JSON.parse(body) : null
}

export function fetchChapter(chapterId) {
  return fetch(`${API_BASE}/chapter/${encodePathSegment(chapterId)}`).then(handleResponse)
}

export function fetchChapters() {
  return fetch(`${API_BASE}/chapter`).then(handleResponse)
}

export function importChapterMarkdown(chapterId, payload) {
  const importUrl = chapterId
    ? `${API_BASE}/chapter/${encodePathSegment(chapterId)}/import`
    : `${API_BASE}/chapter/import`
  return fetch(importUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  }).then(handleResponse)
}

export function deleteChapter(chapterId) {
  return fetch(`${API_BASE}/chapter/${encodePathSegment(chapterId)}`, {
    method: 'DELETE'
  }).then(handleResponse)
}

function buildProgressUrl(chapterId, userId) {
  const url = new URL(`${API_BASE}/progress/${encodePathSegment(chapterId)}`, window.location.origin)
  if (userId) {
    url.searchParams.set('userId', userId)
  }
  return url.toString()
}

export function fetchProgress(chapterId, userId) {
  return fetch(buildProgressUrl(chapterId, userId)).then(handleResponse)
}

export function saveProgress(chapterId, userId, payload) {
  return fetch(buildProgressUrl(chapterId, userId), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  }).then(handleResponse)
}
