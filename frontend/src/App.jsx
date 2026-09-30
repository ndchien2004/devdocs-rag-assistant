import { useCallback, useEffect, useState } from 'react'
import { api } from './api/client.js'
import DocumentSidebar from './components/DocumentSidebar.jsx'
import ChatPage from './pages/ChatPage.jsx'

const SIDEBAR_KEY = 'devdocs.sidebar' // 'collapsed' | 'open' | 'expanded'

function loadSidebarMode() {
  try {
    return localStorage.getItem(SIDEBAR_KEY) ?? 'open'
  } catch {
    return 'open'
  }
}

export default function App() {
  const [sidebarMode, setSidebarMode] = useState(loadSidebarMode)
  const [messages, setMessages] = useState([])
  const [documents, setDocuments] = useState([])
  const [docsLoading, setDocsLoading] = useState(true)
  const [docsError, setDocsError] = useState(null)

  const reloadDocuments = useCallback(async () => {
    try {
      setDocuments(await api.listDocuments())
      setDocsError(null)
    } catch (err) {
      setDocsError(err.message)
    } finally {
      setDocsLoading(false)
    }
  }, [])

  useEffect(() => {
    reloadDocuments()
  }, [reloadDocuments])

  function changeSidebarMode(mode) {
    setSidebarMode(mode)
    try {
      localStorage.setItem(SIDEBAR_KEY, mode)
    } catch {
      // chế độ riêng tư / chặn storage — chỉ mất tính năng ghi nhớ
    }
  }

  const indexedCount = documents.filter((d) => d.status === 'INDEXED').length

  return (
    <div className="flex h-screen overflow-hidden bg-canvas font-sans text-ink">
      <DocumentSidebar
        mode={sidebarMode}
        onModeChange={changeSidebarMode}
        documents={documents}
        loading={docsLoading}
        error={docsError}
        onChanged={reloadDocuments}
      />
      <main className="flex min-w-0 flex-1 flex-col">
        <ChatPage
          messages={messages}
          setMessages={setMessages}
          indexedCount={indexedCount}
          docsLoading={docsLoading}
          onOpenDocuments={() => changeSidebarMode('open')}
        />
      </main>
    </div>
  )
}
