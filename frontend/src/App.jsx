import { useState } from 'react'
import ChatPage from './pages/ChatPage.jsx'
import DocumentsPage from './pages/DocumentsPage.jsx'

const PAGES = [
  { id: 'chat', label: 'Chat' },
  { id: 'documents', label: 'Tài liệu' },
]

export default function App() {
  const [page, setPage] = useState('chat')
  // Giữ lịch sử chat ở App để chuyển tab không bị mất.
  const [messages, setMessages] = useState([])

  return (
    <div className="flex h-screen flex-col bg-slate-50 text-slate-800">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3">
          <h1 className="text-lg font-semibold">📚 DevDocs RAG Assistant</h1>
          <nav className="flex gap-1 rounded-lg bg-slate-100 p-1">
            {PAGES.map((p) => (
              <button
                key={p.id}
                onClick={() => setPage(p.id)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
                  page === p.id ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                {p.label}
              </button>
            ))}
          </nav>
        </div>
      </header>

      <main className="min-h-0 flex-1">
        {page === 'chat' ? <ChatPage messages={messages} setMessages={setMessages} /> : <DocumentsPage />}
      </main>
    </div>
  )
}
