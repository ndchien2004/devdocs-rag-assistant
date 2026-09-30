import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client.js'
import AnswerMessage from '../components/AnswerMessage.jsx'
import TopicSelect from '../components/TopicSelect.jsx'

const MAX_QUESTION = 1000
let nextId = 1

export default function ChatPage({ messages, setMessages }) {
  const [question, setQuestion] = useState('')
  const [topic, setTopic] = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  async function send(e) {
    e?.preventDefault()
    const q = question.trim()
    if (!q || loading) return

    setMessages((prev) => [...prev, { id: nextId++, role: 'user', text: q, topic }])
    setQuestion('')
    setLoading(true)
    try {
      const res = await api.chat({ question: q, topic })
      setMessages((prev) => [...prev, { id: nextId++, role: 'assistant', ...res }])
    } catch (err) {
      setMessages((prev) => [...prev, { id: nextId++, role: 'error', text: err.message }])
    } finally {
      setLoading(false)
    }
  }

  function onKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) send(e)
  }

  return (
    <div className="mx-auto flex h-full max-w-4xl flex-col px-4">
      <div className="flex items-center gap-2 py-3 text-sm">
        <span className="text-slate-500">Chủ đề:</span>
        <TopicSelect value={topic} onChange={setTopic} allLabel="Tất cả" />
      </div>

      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto pb-4">
        {messages.length === 0 && (
          <div className="mt-16 text-center text-slate-400">
            <p className="text-3xl">💬</p>
            <p className="mt-2">Hỏi bất cứ điều gì về tài liệu bạn đã nạp.</p>
            <p className="mt-1 text-sm">Ví dụ: "Khác nhau giữa REQUIRED và REQUIRES_NEW?"</p>
          </div>
        )}

        {messages.map((m) => {
          if (m.role === 'user') {
            return (
              <div key={m.id} className="flex justify-end">
                <div className="max-w-[80%] rounded-2xl rounded-br-sm bg-sky-600 px-4 py-2 text-white">{m.text}</div>
              </div>
            )
          }
          if (m.role === 'error') {
            return (
              <div
                key={m.id}
                data-role="error"
                className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
              >
                ⚠️ {m.text}
              </div>
            )
          }
          return <AnswerMessage key={m.id} message={m} />
        })}

        {loading && (
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sky-600" />
            Đang tìm trong tài liệu và soạn câu trả lời...
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={send} className="border-t border-slate-200 py-3">
        <div className="flex items-end gap-2">
          <textarea
            value={question}
            onChange={(e) => setQuestion(e.target.value.slice(0, MAX_QUESTION))}
            onKeyDown={onKeyDown}
            rows={2}
            placeholder="Nhập câu hỏi... (Enter để gửi, Shift+Enter xuống dòng)"
            className="flex-1 resize-none rounded-lg border border-slate-300 bg-white px-3 py-2 focus:border-sky-500 focus:outline-none"
          />
          <button
            type="submit"
            disabled={loading || !question.trim()}
            className="rounded-lg bg-sky-600 px-5 py-2.5 font-medium text-white transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            Gửi
          </button>
        </div>
        <div className="mt-1 text-right text-xs text-slate-400">
          {question.length}/{MAX_QUESTION}
        </div>
      </form>
    </div>
  )
}
