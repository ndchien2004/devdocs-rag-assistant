import { readSse } from './sse.js'

const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

/** Lỗi từ backend (RFC 9457 Problem Details) hoặc lỗi mạng, đã chuyển thành thông điệp thân thiện. */
export class ApiError extends Error {
  constructor(message, status, problem) {
    super(message)
    this.status = status
    this.problem = problem
  }
}

const networkError = () =>
  new ApiError('Không kết nối được tới backend. Kiểm tra Spring Boot đã chạy ở ' + API_URL + '.', 0)

const problemError = (problem, status) =>
  new ApiError(problem?.detail ?? problem?.title ?? `Lỗi ${status}`, status, problem)

async function readJson(response) {
  const isJson = (response.headers.get('Content-Type') ?? '').includes('json')
  return isJson ? response.json() : null
}

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(`${API_URL}${path}`, options)
  } catch {
    throw networkError()
  }
  if (response.status === 204) return null

  const body = await readJson(response)
  if (!response.ok) throw problemError(body, response.status)
  return body
}

/**
 * POST rồi đọc Server-Sent Events: gọi onSources(nguồn ứng viên) rồi onToken(text) cho từng mẩu, trả về kết quả cuối
 * (sự kiện done). Hủy bằng signal → ném AbortError và backend dừng gọi LLM.
 */
async function postStream(path, body, { signal, onSources, onToken } = {}) {
  let response
  try {
    response = await fetch(`${API_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(body),
      signal,
    })
  } catch (err) {
    if (err.name === 'AbortError') throw err
    throw networkError()
  }
  // Lỗi trước khi stream bắt đầu (400, 404, 503) vẫn là HTTP status + Problem Details như request thường.
  if (!response.ok) throw problemError(await readJson(response), response.status)

  for await (const { event, data } of readSse(response.body)) {
    if (event === 'sources') onSources?.(data)
    else if (event === 'token') onToken?.(data.text)
    else if (event === 'done') return data
    else if (event === 'error') throw problemError(data, data.status)
  }
  throw new ApiError('Kết nối bị ngắt trước khi nhận đủ câu trả lời.', 0)
}

/** Hỏi đáp một lượt dạng streaming (chỉ mode MANUAL). */
const chatStream = ({ question, topic, topK, ...handlers }) =>
  postStream('/api/v1/chat/stream', { question, topic: topic || null, topK: topK || null }, handlers)

const json = (method, body) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  chatStream,

  /** topic / topK / mode bỏ trống = để backend dùng mặc định (mọi chủ đề, app.rag.top-k, MANUAL). */
  chat: ({ question, topic, topK, mode }) =>
    request('/api/v1/chat', json('POST', { question, topic: topic || null, topK: topK || null, mode: mode || null })),

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

  listConversations: () => request('/api/v1/conversations'),
  getConversation: (id) => request(`/api/v1/conversations/${id}`),
  createConversation: (documentIds) => request('/api/v1/conversations', json('POST', { documentIds })),
  renameConversation: (id, title) => request(`/api/v1/conversations/${id}`, json('PATCH', { title })),
  setConversationScope: (id, documentIds) => request(`/api/v1/conversations/${id}/scope`, json('PUT', { documentIds })),
  deleteConversation: (id) => request(`/api/v1/conversations/${id}`, { method: 'DELETE' }),
  /** Hỏi trong hội thoại dạng streaming; backend lưu xong hai tin nhắn rồi mới gửi done. */
  askConversationStream: (id, { question, topic, topK, ...handlers }) =>
    postStream(`/api/v1/conversations/${id}/messages/stream`,
      { question, topic: topic || null, topK: topK || null }, handlers),
  askConversation: (id, question, topic) =>
    request(`/api/v1/conversations/${id}/messages`, json('POST', { question, topic: topic || null })),
  pdfUrl: (id) => `${API_URL}/api/v1/documents/${id}/content`,
}
