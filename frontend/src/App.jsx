import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from './api/client.js'
import DocumentSidebar from './components/DocumentSidebar.jsx'
import LiquidBackground from './components/LiquidBackground.jsx'
import { LoadingOverlay } from './components/Overlay.jsx'
import ChatPage from './pages/ChatPage.jsx'
const PdfViewer = lazy(() => import('./components/PdfViewer.jsx'))

const SIDEBAR_KEY = 'devdocs.sidebar'
function loadSidebarMode() {
  try { return localStorage.getItem(SIDEBAR_KEY) ?? 'open' } catch { return 'open' }
}

export default function App() {
  const [sidebarMode, setSidebarMode] = useState(loadSidebarMode)
  const [documents, setDocuments] = useState([])
  const [docsLoading, setDocsLoading] = useState(true)
  const [docsError, setDocsError] = useState(null)
  const [conversations, setConversations] = useState([])
  const [conversation, setConversation] = useState(null)
  const [conversationLoading, setConversationLoading] = useState(true)
  const [sessionError, setSessionError] = useState(null)
  const [activePdf, setActivePdf] = useState(null)

  const reloadDocuments = useCallback(async () => {
    try { setDocuments(await api.listDocuments()); setDocsError(null) }
    catch (err) { setDocsError('Không tải được danh sách tài liệu. Hãy thử tải lại.') }
    finally { setDocsLoading(false) }
  }, [])

  const reloadConversations = useCallback(async () => setConversations(await api.listConversations()), [])

  // StrictMode (dev) chạy effect hai lần: giữ promise trong ref để chỉ gọi API khởi tạo một lần,
  // nếu không lần đầu mở app sẽ tạo hai cuộc trò chuyện trống.
  const bootRef = useRef(null)
  useEffect(() => {
    let current = true
    bootRef.current ??= (() => {
      const docs = api.listDocuments()
      const session = docs.then(async (list) => {
        let sessions = await api.listConversations()
        if (sessions.length) return { sessions, active: await api.getConversation(sessions[0].id) }
        const active = await api.createConversation(list.filter((d) => d.status === 'INDEXED').map((d) => d.id))
        sessions = await api.listConversations()
        return { sessions, active }
      })
      return { docs, session }
    })()
    const { docs, session } = bootRef.current
    docs.then(
      (list) => { if (current) { setDocuments(list); setDocsLoading(false) } },
      () => { if (current) { setDocsError('Không tải được danh sách tài liệu. Hãy thử tải lại.'); setDocsLoading(false) } },
    )
    session
      .then(({ sessions, active }) => { if (current) { setConversation(active); setConversations(sessions) } })
      .catch(() => { if (current) setSessionError('Không tải được cuộc trò chuyện. Hãy thử tải lại.') })
      .finally(() => { if (current) setConversationLoading(false) })
    return () => { current = false }
  }, [])

  async function createConversation() {
    setConversationLoading(true); setSessionError(null)
    try {
      const created = await api.createConversation(documents.filter((d) => d.status === 'INDEXED').map((d) => d.id))
      setConversation(created); await reloadConversations()
    } catch { setSessionError('Không thể tạo cuộc trò chuyện. Hãy thử lại.') }
    finally { setConversationLoading(false) }
  }

  async function openConversation(id) {
    setConversationLoading(true); setSessionError(null)
    try { setConversation(await api.getConversation(id)) }
    catch { setSessionError('Không thể mở cuộc trò chuyện đã lưu.') }
    finally { setConversationLoading(false) }
  }

  async function renameConversation(id, title) {
    try {
      const updated = await api.renameConversation(id, title)
      if (conversation?.id === id) setConversation(updated)
      await reloadConversations()
    } catch { setSessionError('Không thể đổi tên cuộc trò chuyện.') }
  }

  async function removeConversation(id) {
    try {
      await api.deleteConversation(id)
      const sessions = await api.listConversations(); setConversations(sessions)
      if (conversation?.id === id) {
        if (sessions.length) setConversation(await api.getConversation(sessions[0].id))
        else await createConversation()
      }
    } catch { setSessionError('Không thể xóa cuộc trò chuyện.') }
  }

  async function updateScope(documentIds) {
    if (!conversation) return
    try {
      setConversation(await api.setConversationScope(conversation.id, documentIds))
    } catch { setSessionError('Phạm vi chỉ có thể gồm tài liệu đã index.') }
  }

  async function refreshDocuments() {
    await reloadDocuments()
    if (conversation) {
      try { setConversation(await api.getConversation(conversation.id)) } catch { /* phiên hiện tại có thể vừa bị xóa */ }
    }
  }

  const messages = useMemo(() => (conversation?.messages ?? []).map((m) => m.role === 'USER'
    ? { id: m.id, role: 'user', text: m.content }
    : { id: m.id, role: 'assistant', answer: m.content, found: m.found, sources: m.sources ?? [] }), [conversation])
  const indexedCount = documents.filter((d) => d.status === 'INDEXED').length
  function openPdf(source) {
    if (source.fileName?.toLowerCase().endsWith('.pdf') && documents.some((d) => d.id === source.documentId)) setActivePdf(source)
  }
  function applyConversationUpdate(updated) {
    setConversation((current) => current?.id === updated.id ? updated : current)
  }

  function changeSidebarMode(mode) {
    setSidebarMode(mode)
    try { localStorage.setItem(SIDEBAR_KEY, mode) } catch { /* localStorage tùy chọn */ }
  }

  return (
    <div className="relative isolate flex h-screen gap-3 overflow-hidden p-3 font-sans text-ink">
      <LiquidBackground />
      <DocumentSidebar
        mode={sidebarMode} onModeChange={changeSidebarMode} documents={documents} loading={docsLoading}
        error={docsError} onChanged={refreshDocuments} conversations={conversations}
        activeConversationId={conversation?.id} onNewConversation={createConversation}
        onOpenConversation={openConversation} onRenameConversation={renameConversation}
        onDeleteConversation={removeConversation}
      />
      <main className="flex min-w-0 flex-1 flex-col">
        <ChatPage
          conversation={conversation} messages={messages} indexedCount={indexedCount} docsLoading={docsLoading}
          documents={documents} loading={conversationLoading} error={sessionError}
          onConversationChange={applyConversationUpdate} onScopeChange={updateScope}
          onConversationsChanged={reloadConversations} onOpenDocuments={() => changeSidebarMode('open')}
          onNewConversation={createConversation} onOpenPdf={openPdf}
        />
      </main>
      {activePdf && <Suspense fallback={<LoadingOverlay>Đang tải trình xem PDF…</LoadingOverlay>}><PdfViewer key={`${activePdf.documentId}-${activePdf.pageNumber}`} url={api.pdfUrl(activePdf.documentId)} fileName={activePdf.fileName} fileSize={documents.find((d) => d.id === activePdf.documentId)?.fileSize} initialPage={activePdf.pageNumber ?? 1} onClose={() => setActivePdf(null)} /></Suspense>}
    </div>
  )
}
