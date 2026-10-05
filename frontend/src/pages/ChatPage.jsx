import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { api } from '../api/client.js'
import Composer from '../components/Composer.jsx'
import { BookIcon, NewChatIcon } from '../components/icons.jsx'

const AnswerMessage = lazy(() => import('../components/AnswerMessage.jsx'))

const SUGGESTIONS = ['REQUIRED và REQUIRES_NEW khác nhau thế nào?', 'N+1 query là gì, khắc phục ra sao?', 'Vì sao phải override hashCode khi override equals?']

function Thinking() {
  return <div className="flex items-center gap-2.5 text-sm text-muted" role="status"><span className="thinking-dot h-1.5 w-1.5 rounded-full bg-accent" />Đang tìm trong tài liệu…</div>
}
function Disclaimer() {
  return <p className="mt-2 text-center text-xs text-muted">Câu trả lời chỉ dựa trên tài liệu đã chọn và có thể sai — hãy kiểm tra nguồn trích dẫn.</p>
}

function ScopePicker({ documents, selectedIds, onChange, disabled }) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(selectedIds)
  return (
    <div className="relative">
      <button type="button" onClick={() => { setDraft(selectedIds); setOpen((v) => !v) }} disabled={disabled} className="rounded-lg border border-line px-2.5 py-1.5 text-xs text-ink-soft hover:bg-hover disabled:opacity-50">
        Tài liệu: {selectedIds.length} đã chọn ▾
      </button>
      {open && <div className="absolute right-0 z-20 mt-2 max-h-72 w-72 overflow-y-auto rounded-xl border border-line bg-white p-2 shadow-xl">
        <p className="px-2 py-1 text-xs text-muted">Chỉ tài liệu đã index có thể dùng để hỏi.</p>
        {documents.filter((d) => d.status === 'INDEXED').map((doc) => <label key={doc.id} className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-2 text-sm hover:bg-hover">
          <input type="checkbox" checked={draft.includes(doc.id)} onChange={(e) => {
            const next = e.target.checked ? [...draft, doc.id] : draft.filter((id) => id !== doc.id)
            setDraft(next)
          }} />
          <span className="truncate">{doc.fileName}</span>
        </label>)}
        {documents.every((d) => d.status !== 'INDEXED') && <p className="px-2 py-3 text-sm text-muted">Chưa có tài liệu sẵn sàng.</p>}
        <div className="mt-2 flex justify-end gap-2 border-t border-line px-2 pt-2">
          <button type="button" onClick={() => setOpen(false)} className="rounded px-2 py-1 text-xs text-muted hover:bg-hover">Hủy</button>
          <button type="button" onClick={() => { onChange(draft); setOpen(false) }} className="rounded bg-accent px-2.5 py-1 text-xs text-white">Lưu phạm vi</button>
        </div>
      </div>}
    </div>
  )
}

export default function ChatPage({ conversation, messages, indexedCount, docsLoading, documents, loading, error,
  onConversationChange, onScopeChange, onConversationsChanged, onNewConversation, onOpenDocuments, onOpenPdf }) {
  const [topic, setTopic] = useState('')
  const [sending, setSending] = useState(false)
  const [temporary, setTemporary] = useState([])
  const [localError, setLocalError] = useState(null)
  const bottomRef = useRef(null)

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, temporary, sending])
  useEffect(() => { setTemporary([]); setLocalError(null); setTopic('') }, [conversation?.id])

  async function ask(question) {
    if (sending || !conversation) return
    setTemporary([{ id: `pending-${Date.now()}`, role: 'user', text: question }])
    setLocalError(null); setSending(true)
    try {
      const updated = await api.askConversation(conversation.id, question, topic)
      onConversationChange(updated); setTemporary([]); await onConversationsChanged()
    } catch (err) {
      setTemporary([{ id: `failed-${Date.now()}`, role: 'user', text: question }])
      setLocalError(err.status === 503 ? 'Dịch vụ AI hiện chưa sẵn sàng. Hãy thử lại sau.' : 'Không thể tạo câu trả lời. Hãy thử lại.')
    } finally { setSending(false) }
  }

  const allMessages = [...messages, ...temporary]
  const composer = <Composer onSend={ask} disabled={sending || loading || !conversation} topic={topic} onTopicChange={setTopic} autoFocus />

  if (loading && !conversation) return <div className="grid h-full place-items-center text-sm text-muted" role="status">Đang mở cuộc trò chuyện…</div>
  if (!conversation) return <div className="m-auto max-w-lg px-6 text-center"><p className="text-sm text-danger">{error ?? 'Chưa có cuộc trò chuyện.'}</p><button onClick={onNewConversation} className="mt-3 rounded-lg bg-accent px-4 py-2 text-sm text-white">Tạo cuộc trò chuyện</button></div>

  if (allMessages.length === 0) {
    return <div className="flex h-full flex-col items-center justify-center overflow-y-auto px-4 pb-16">
      <div className="mb-3 flex w-full max-w-2xl items-center justify-between gap-3">
        <h1 className="truncate font-serif text-xl">{conversation.title}</h1>
        <ScopePicker documents={documents} selectedIds={conversation.documentIds ?? []} onChange={onScopeChange} disabled={sending} />
      </div>
      <BookIcon className="mb-4 h-8 w-8 text-accent" />
      <h2 className="text-center font-serif text-3xl tracking-tight text-ink md:text-[2.1rem]">Hôm nay bạn muốn ôn lại gì?</h2>
      <p className="mt-2 text-center text-sm text-muted">{docsLoading ? 'Đang tải danh sách tài liệu…' : indexedCount > 0 ? `Hỏi trên ${conversation.documentIds?.length ?? 0} tài liệu trong phạm vi cuộc trò chuyện.` : <>Chưa có tài liệu sẵn sàng. <button onClick={onOpenDocuments} className="text-accent hover:underline">Nạp tài liệu</button> để bắt đầu.</>}</p>
      {conversation.documentIds?.length === 0 && <p className="mt-2 text-xs text-warn">Hãy chọn ít nhất một tài liệu trong phạm vi để hỏi.</p>}
      <div className="mt-8 w-full max-w-2xl">{composer}</div>
      <div className="mt-4 flex max-w-2xl flex-wrap justify-center gap-2">{SUGGESTIONS.map((s) => <button key={s} onClick={() => ask(s)} disabled={!conversation.documentIds?.length} className="rounded-lg border border-line bg-canvas px-3 py-1.5 text-sm text-ink-soft transition hover:border-line-strong hover:bg-white hover:text-ink disabled:opacity-50">{s}</button>)}</div>
    </div>
  }

  return <div className="flex h-full flex-col">
    <header className="flex h-14 shrink-0 items-center justify-between gap-2 px-4">
      <h1 className="truncate text-sm font-medium text-ink-soft">{conversation.title}</h1>
      <div className="flex items-center gap-2"><ScopePicker documents={documents} selectedIds={conversation.documentIds ?? []} onChange={onScopeChange} disabled={sending} />
        <button onClick={onNewConversation} disabled={sending} className="inline-flex shrink-0 items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm text-ink-soft transition hover:bg-hover hover:text-ink disabled:opacity-40"><NewChatIcon className="h-4 w-4" />Cuộc trò chuyện mới</button>
      </div>
    </header>
    <div className="min-h-0 flex-1 overflow-y-auto"><div className="mx-auto max-w-3xl space-y-7 px-4 pt-2 pb-8">
      {allMessages.map((m) => m.role === 'user'
        ? <div key={m.id} className="flex justify-end"><div className="max-w-[85%] rounded-2xl bg-bubble px-4 py-2.5 leading-relaxed whitespace-pre-wrap text-ink">{m.text}</div></div>
        : m.role === 'error' ? <div key={m.id} className="rounded-xl border border-danger/25 bg-danger/5 px-4 py-3 text-sm text-danger">{m.text}</div>
          : <Suspense key={m.id} fallback={<div role="status" className="text-sm text-muted">Đang tải câu trả lời…</div>}><AnswerMessage message={m} onOpenPdf={onOpenPdf} availableDocumentIds={documents.map((d) => d.id)} /></Suspense>)}
      {sending && <Thinking />}{localError && <p role="alert" className="rounded-xl border border-danger/25 bg-danger/5 px-4 py-3 text-sm text-danger">{localError}</p>}{error && <p role="alert" className="text-sm text-danger">{error}</p>}
      <div ref={bottomRef} />
    </div></div>
    <div className="mx-auto w-full max-w-3xl shrink-0 px-4 pb-4">{composer}<Disclaimer /></div>
  </div>
}
