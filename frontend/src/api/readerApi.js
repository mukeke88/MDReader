const API_BASE = 'http://localhost:18080/api'

function encodePathSegment(value) {
  return encodeURIComponent(value)
}

async function handleResponse(response) {
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`)
  }
  return response.json()
}

export function fetchChapter(chapterId) {
  return fetch(`${API_BASE}/chapter/${encodePathSegment(chapterId)}`).then(handleResponse)
}

export function importChapter(chapterId, payload) {
  return fetch(`${API_BASE}/chapter/${encodePathSegment(chapterId)}/import`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  }).then(handleResponse)
}

export function fetchProgress(chapterId) {
  return fetch(`${API_BASE}/progress/${encodePathSegment(chapterId)}`).then(handleResponse)
}

export function saveProgress(chapterId, payload) {
  return fetch(`${API_BASE}/progress/${encodePathSegment(chapterId)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  }).then(handleResponse)
}

export function exportProgressTable() {
  return fetch(`${API_BASE}/progress/export`, {
    method: 'POST'
  }).then(handleResponse)
}
