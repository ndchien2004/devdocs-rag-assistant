const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

/** Lỗi từ backend (RFC 9457 Problem Details) hoặc lỗi mạng, đã chuyển thành thông điệp thân thiện. */
export class ApiError extends Error {
  constructor(message, status, problem) {
    super(message)
    this.status = status
    this.problem = problem
  }
}

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(`${API_URL}${path}`, options)
  } catch {
    throw new ApiError('Không kết nối được tới backend. Kiểm tra Spring Boot đã chạy ở ' + API_URL + '.', 0)
  }
  if (response.status === 204) return null

  const isJson = (response.headers.get('Content-Type') ?? '').includes('json')
  const body = isJson ? await response.json() : null
  if (!response.ok) {
    const message = body?.detail ?? body?.title ?? `Lỗi ${response.status}`
    throw new ApiError(message, response.status, body)
  }
  return body
}

const json = (method, body) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  chat: ({ question, topic, topK }) =>
    request('/api/v1/chat', json('POST', { question, topic: topic || null, topK: topK || null })),

  listDocuments: ({ topic, status } = {}) => {
    const params = new URLSearchParams()
    if (topic) params.set('topic', topic)
    if (status) params.set('status', status)
    const query = params.toString()
    return request(`/api/v1/documents${query ? `?${query}` : ''}`)
  },

  uploadDocument: (file, topic) => {
    const form = new FormData()
    form.append('file', file)
    form.append('topic', topic)
    return request('/api/v1/documents', { method: 'POST', body: form })
  },

  deleteDocument: (id) => request(`/api/v1/documents/${id}`, { method: 'DELETE' }),

  reindexDocument: (id) => request(`/api/v1/documents/${id}/reindex`, { method: 'POST' }),
}
