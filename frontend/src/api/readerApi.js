const API_BASE = 'http://localhost:8080/api'

async function handleResponse(response) {
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`)
  }
  return response.json()
}

export function fetchChapter(chapterId) {
  return fetch(`${API_BASE}/chapter/${chapterId}`).then(handleResponse)
}

export function fetchProgress(chapterId) {
  return fetch(`${API_BASE}/progress/${chapterId}`).then(handleResponse)
}

export function saveProgress(chapterId, payload) {
  return fetch(`${API_BASE}/progress/${chapterId}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  }).then(handleResponse)
}
