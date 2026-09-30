import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client.js'
import AnswerMessage from '../components/AnswerMessage.jsx'
import Composer from '../components/Composer.jsx'
import { BookIcon, NewChatIcon } from '../components/icons.jsx'

const SUGGESTIONS = [
  'REQUIRED và REQUIRES_NEW khác nhau thế nào?',
  'N+1 query là gì, khắc phục ra sao?',
  'Vì sao phải override hashCode khi override equals?',
]

let nextId = 1

function Thinking() {
  return (
    <div className="flex items-center gap-2.5 text-sm text-muted" role="status">
      <span className="flex gap-1">
        {[0, 1, 2].map((i) => (
          <span key={i} className="thinking-dot h-1.5 w-1.5 rounded-full bg-accent" style={{ animationDelay: `${i * 0.15}s` }} />
        ))}
      </span>
      Đang tìm trong tài liệu…
    </div>
  )
}

function Disclaimer() {
  return (
    <p className="mt-2 text-center text-xs text-muted">
      Câu trả lời chỉ dựa trên tài liệu đã nạp và có thể sai — hãy kiểm tra nguồn trích dẫn.
    </p>
  )
}

export default function ChatPage({ messages, setMessages, indexedCount, docsLoading, onOpenDocuments }) {
  const [topic, setTopic] = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  async function ask(question) {
    if (loading) return
    setMessages((prev) => [...prev, { id: nextId++, role: 'user', text: question }])
    setLoading(true)
    try {
      const res = await api.chat({ question, topic })
      setMessages((prev) => [...prev, { id: nextId++, role: 'assistant', ...res }])
    } catch (err) {
      setMessages((prev) => [...prev, { id: nextId++, role: 'error', text: err.message }])
    } finally {
      setLoading(false)
    }
  }

  const composer = <Composer onSend={ask} disabled={loading} topic={topic} onTopicChange={setTopic} autoFocus />

  // --- Màn hình chào (chưa có tin nhắn)
  if (messages.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center overflow-y-auto px-4 pb-16">
        <BookIcon className="mb-4 h-8 w-8 text-accent" />
        <h1 className="text-center font-serif text-3xl tracking-tight text-ink md:text-[2.1rem]">
          Hôm nay bạn muốn ôn lại gì?
        </h1>
        <p className="mt-2 text-center text-sm text-muted">
          {docsLoading ? (
            'Đang tải danh sách tài liệu…'
          ) : indexedCount > 0 ? (
            <>
              Hỏi trên {indexedCount} tài liệu đã nạp · mọi câu trả lời đều kèm nguồn
            </>
          ) : (
            <>
              Chưa có tài liệu nào.{' '}
              <button onClick={onOpenDocuments} className="text-accent hover:underline">
                Nạp tài liệu
              </button>{' '}
              để bắt đầu.
            </>
          )}
        </p>
        <div className="mt-8 w-full max-w-2xl">{composer}</div>
        <div className="mt-4 flex max-w-2xl flex-wrap justify-center gap-2">
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              onClick={() => ask(s)}
              className="rounded-lg border border-line bg-canvas px-3 py-1.5 text-sm text-ink-soft transition hover:border-line-strong hover:bg-white hover:text-ink"
            >
              {s}
            </button>
          ))}
        </div>
      </div>
    )
  }

  // --- Hội thoại
  return (
    <div className="flex h-full flex-col">
      <header className="flex h-14 shrink-0 items-center justify-end px-4">
        <button
          onClick={() => setMessages([])}
          disabled={loading}
          className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm text-ink-soft transition hover:bg-hover hover:text-ink disabled:opacity-40"
        >
          <NewChatIcon className="h-4 w-4" />
          Cuộc trò chuyện mới
        </button>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-3xl space-y-7 px-4 pt-2 pb-8">
          {messages.map((m) => {
            if (m.role === 'user') {
              return (
                <div key={m.id} className="flex justify-end">
                  <div className="max-w-[85%] rounded-2xl bg-bubble px-4 py-2.5 leading-relaxed whitespace-pre-wrap text-ink">
                    {m.text}
                  </div>
                </div>
              )
            }
            if (m.role === 'error') {
              return (
                <div
                  key={m.id}
                  data-role="error"
                  className="rounded-xl border border-danger/25 bg-danger/5 px-4 py-3 text-sm text-danger"
                >
                  {m.text}
                </div>
              )
            }
            return <AnswerMessage key={m.id} message={m} />
          })}
          {loading && <Thinking />}
          <div ref={bottomRef} />
        </div>
      </div>

      <div className="mx-auto w-full max-w-3xl shrink-0 px-4 pb-4">
        {composer}
        <Disclaimer />
      </div>
    </div>
  )
}
