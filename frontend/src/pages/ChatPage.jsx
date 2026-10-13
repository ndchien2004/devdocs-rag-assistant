import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { api } from '../api/client.js'
import { DEFAULT_CHAT_OPTIONS } from '../constants.js'
import Composer from '../components/Composer.jsx'
import { BookIcon, ChevronDownIcon, NewChatIcon } from '../components/icons.jsx'

const AnswerMessage = lazy(() => import('../components/AnswerMessage.jsx'))

const SUGGESTIONS = ['REQUIRED và REQUIRES_NEW khác nhau thế nào?', 'N+1 query là gì, khắc phục ra sao?', 'Vì sao phải override hashCode khi override equals?']

function Thinking() {
  return (
    <div className="glass-thin inline-flex items-center gap-2.5 rounded-full px-4 py-2 text-sm text-muted" role="status">
      <span className="flex gap-1">
        {[0, 1, 2].map((i) => <span key={i} className="thinking-dot h-1.5 w-1.5 rounded-full bg-accent" style={{ animationDelay: `${i * 0.15}s` }} />)}
      </span>
      Đang tìm trong tài liệu…
    </div>
  )
}
function Disclaimer() {
  return <p className="mt-2 text-center text-xs text-muted">Câu trả lời chỉ dựa trên tài liệu đã chọn và có thể sai — hãy kiểm tra nguồn trích dẫn.</p>
}

function ScopePicker({ documents, selectedIds, onChange, disabled }) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(selectedIds)
  const rootRef = useRef(null)

  // Bấm ra ngoài / Esc → đóng (bỏ thay đổi chưa lưu), giống popup "Tùy chọn nâng cao".
  useEffect(() => {
    if (!open) return
    const onPointerDown = (e) => { if (!rootRef.current?.contains(e.target)) setOpen(false) }
    const onKeyDown = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div ref={rootRef} className="relative">
      <button type="button" onClick={() => { setDraft(selectedIds); setOpen((v) => !v) }} disabled={disabled} aria-expanded={open}
        className="chip inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-medium text-ink-soft disabled:opacity-50">
        Tài liệu: {selectedIds.length} đã chọn <ChevronDownIcon className="h-3.5 w-3.5 text-muted" />
      </button>
      {open && <div className="glass-strong absolute right-0 z-30 mt-2 max-h-80 w-80 overflow-y-auto rounded-3xl p-2.5">
        <p className="px-2.5 py-1.5 text-xs text-muted">Chỉ tài liệu đã index có thể dùng để hỏi.</p>
        {documents.filter((d) => d.status === 'INDEXED').map((doc) => <label key={doc.id} className="flex cursor-pointer items-center gap-2.5 rounded-2xl px-2.5 py-2 text-sm transition hover:bg-hover">
          <input type="checkbox" className="h-4 w-4 accent-[var(--color-accent)]" checked={draft.includes(doc.id)} onChange={(e) => {
            const next = e.target.checked ? [...draft, doc.id] : draft.filter((id) => id !== doc.id)
            setDraft(next)
          }} />
          <span className="truncate">{doc.fileName}</span>
        </label>)}
        {documents.every((d) => d.status !== 'INDEXED') && <p className="px-2.5 py-3 text-sm text-muted">Chưa có tài liệu sẵn sàng.</p>}
        <div className="mt-2 flex justify-end gap-2 border-t border-line px-1.5 pt-2.5">
          <button type="button" onClick={() => setOpen(false)} className="rounded-full px-3 py-1.5 text-xs text-ink-soft transition hover:bg-hover">Hủy</button>
          <button type="button" onClick={() => { onChange(draft); setOpen(false) }} className="btn-cyan px-3.5 py-1.5 text-xs font-medium">Lưu phạm vi</button>
        </div>
      </div>}
    </div>
  )
}

/** Thanh tiêu đề kính dạng viên, dùng chung cho màn chào và màn hội thoại. */
function ChatHeader({ title, children }) {
  return (
    <header className="glass relative z-20 flex h-14 shrink-0 items-center justify-between gap-3 rounded-full pr-2 pl-5">
      <h1 className="truncate text-sm font-semibold text-ink">{title}</h1>
      <div className="flex shrink-0 items-center gap-2">{children}</div>
    </header>
  )
}

export default function ChatPage({ conversation, messages, indexedCount, docsLoading, documents, loading, error,
  onConversationChange, onScopeChange, onConversationsChanged, onNewConversation, onOpenDocuments, onOpenPdf }) {
  const [topic, setTopic] = useState('')
  const [sending, setSending] = useState(false)
  const [temporary, setTemporary] = useState([])
  const [options, setOptions] = useState(DEFAULT_CHAT_OPTIONS)
  const [localError, setLocalError] = useState(null)
  const bottomRef = useRef(null)
  const abortRef = useRef(null)

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, temporary, sending])
  // Đổi cuộc trò chuyện / rời trang khi đang stream → hủy request để backend dừng gọi LLM.
  useEffect(() => { abortRef.current?.abort(); setTemporary([]); setLocalError(null); setTopic('') }, [conversation?.id])
  useEffect(() => () => abortRef.current?.abort(), [])

  /**
   * Câu trả lời hiện dần theo từng token (streaming). Backend lưu xong câu hỏi + câu trả lời rồi mới gửi "done",
   * nên khi xong chỉ cần tải lại cuộc trò chuyện. Dừng / lỗi giữa chừng thì backend không lưu gì.
   */
  async function ask(question) {
    if (sending || !conversation) return
    const conversationId = conversation.id
    const answerId = `answer-${Date.now()}`
    const updateAnswer = (patch) => setTemporary((prev) => prev.some((m) => m.id === answerId)
      ? prev.map((m) => (m.id === answerId ? { ...m, ...patch(m) } : m))
      : [...prev, { id: answerId, role: 'assistant', answer: '', candidates: [], sources: [], ...patch({ answer: '' }) }])
    setTemporary([{ id: `pending-${Date.now()}`, role: 'user', text: question }])
    setLocalError(null); setSending(true)
    const controller = new AbortController()
    abortRef.current = controller
    try {
      await api.askConversationStream(conversationId, {
        question, topic, topK: Number(options.topK) || null, signal: controller.signal,
        onSources: (candidates) => updateAnswer(() => ({ streaming: true, candidates })),
        onToken: (text) => updateAnswer((m) => ({ streaming: true, answer: m.answer + text })),
      })
      const updated = await api.getConversation(conversationId)
      onConversationChange(updated); setTemporary([]); await onConversationsChanged()
    } catch (err) {
      if (err.name === 'AbortError') {
        updateAnswer(() => ({ streaming: false, stopped: true, found: true }))
        return
      }
      setTemporary((prev) => prev.map((m) => (m.id === answerId ? { ...m, streaming: false, interrupted: true } : m)))
      setLocalError(err.status === 503 ? 'Dịch vụ AI hiện chưa sẵn sàng. Hãy thử lại sau.' : 'Không thể tạo câu trả lời. Hãy thử lại.')
    } finally {
      if (abortRef.current === controller) abortRef.current = null
      setSending(false)
    }
  }

  const allMessages = [...messages, ...temporary]
  // Chấm "đang tìm" chỉ hiện tới khi có chữ đầu tiên của câu trả lời.
  const streamingAnswer = temporary.find((m) => m.role === 'assistant' && m.streaming && m.answer)
  const composer = <Composer onSend={ask} onStop={() => abortRef.current?.abort()} disabled={sending || loading || !conversation}
    topic={topic} onTopicChange={setTopic} options={options} onOptionsChange={setOptions} showModeOption={false} autoFocus />

  const scope = <ScopePicker documents={documents} selectedIds={conversation?.documentIds ?? []} onChange={onScopeChange} disabled={sending} />
  const newChat = (
    <button onClick={onNewConversation} disabled={sending} className="chip inline-flex shrink-0 items-center gap-1.5 px-3.5 py-1.5 text-xs font-medium text-ink-soft disabled:opacity-40">
      <NewChatIcon className="h-3.5 w-3.5" />Cuộc trò chuyện mới
    </button>
  )

  if (loading && !conversation) return <div className="grid h-full place-items-center text-sm text-muted" role="status">Đang mở cuộc trò chuyện…</div>
  if (!conversation) return (
    <div className="glass m-auto max-w-lg rounded-3xl px-8 py-7 text-center">
      <p className="text-sm text-danger">{error ?? 'Chưa có cuộc trò chuyện.'}</p>
      <button onClick={onNewConversation} className="btn-cyan mt-4 px-5 py-2 text-sm font-medium">Tạo cuộc trò chuyện</button>
    </div>
  )

  if (allMessages.length === 0) {
    return <div className="flex h-full flex-col">
      <ChatHeader title={conversation.title}>{scope}</ChatHeader>
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center overflow-y-auto px-4 pb-16">
        <div className="glass liquid-sheen relative mb-6 grid h-16 w-16 place-items-center rounded-[22px]">
          <BookIcon className="h-7 w-7 text-accent" />
        </div>
        <h2 className="bg-gradient-to-r from-ink via-ink-soft to-accent-strong bg-clip-text pb-1 text-center text-3xl font-semibold tracking-tight text-transparent md:text-[2.5rem]">Hôm nay bạn muốn ôn lại gì?</h2>
        <p className="mt-2 text-center text-sm text-muted">{docsLoading ? 'Đang tải danh sách tài liệu…' : indexedCount > 0 ? `Hỏi trên ${conversation.documentIds?.length ?? 0} tài liệu trong phạm vi cuộc trò chuyện.` : <>Chưa có tài liệu sẵn sàng. <button onClick={onOpenDocuments} className="font-medium text-accent-strong hover:underline">Nạp tài liệu</button> để bắt đầu.</>}</p>
        {conversation.documentIds?.length === 0 && <p className="mt-2 text-xs text-warn">Hãy chọn ít nhất một tài liệu trong phạm vi để hỏi.</p>}
        <div className="mt-9 w-full max-w-2xl">{composer}</div>
        <div className="mt-5 flex max-w-2xl flex-wrap justify-center gap-2">{SUGGESTIONS.map((s) => <button key={s} onClick={() => ask(s)} disabled={!conversation.documentIds?.length} className="chip px-4 py-2 text-sm text-ink-soft hover:text-ink disabled:opacity-50">{s}</button>)}</div>
      </div>
    </div>
  }

  return <div className="flex h-full flex-col">
    <ChatHeader title={conversation.title}>{scope}{newChat}</ChatHeader>
    <div className="min-h-0 flex-1 overflow-y-auto"><div className="mx-auto max-w-3xl space-y-6 px-4 pt-6 pb-8">
      {allMessages.map((m) => m.role === 'user'
        ? <div key={m.id} className="flex justify-end"><div className="bg-bubble max-w-[85%] rounded-3xl rounded-br-lg border border-white/70 bg-gradient-to-br from-cyan-50/90 to-sky-100/80 px-4.5 py-2.5 leading-relaxed whitespace-pre-wrap text-ink shadow-[0_2px_12px_rgba(8,145,178,0.10),inset_0_1px_0_rgba(255,255,255,0.9)] backdrop-blur-md">{m.text}</div></div>
        : m.role === 'error' ? <div key={m.id} className="rounded-2xl border border-danger/20 bg-rose-50/80 px-4 py-3 text-sm text-danger backdrop-blur-md">{m.text}</div>
          : <Suspense key={m.id} fallback={<div role="status" className="text-sm text-muted">Đang tải câu trả lời…</div>}><AnswerMessage message={m} onOpenPdf={onOpenPdf} availableDocumentIds={documents.map((d) => d.id)} /></Suspense>)}
      {sending && !streamingAnswer && <Thinking />}{localError && <p role="alert" className="rounded-2xl border border-danger/20 bg-rose-50/80 px-4 py-3 text-sm text-danger backdrop-blur-md">{localError}</p>}{error && <p role="alert" className="text-sm text-danger">{error}</p>}
      <div ref={bottomRef} />
    </div></div>
    <div className="mx-auto w-full max-w-3xl shrink-0 px-4">{composer}<Disclaimer /></div>
  </div>
}
