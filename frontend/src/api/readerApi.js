const configuredApiBase = window.MDREADER_CONFIG?.apiBaseUrl || '/api'
const API_BASE = configuredApiBase.replace(/\/+$/, '')

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
