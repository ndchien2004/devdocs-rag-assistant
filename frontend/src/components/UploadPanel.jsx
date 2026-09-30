import { useRef, useState } from 'react'
import { api } from '../api/client.js'
import TopicSelect from './TopicSelect.jsx'
import { UploadIcon, XIcon, IconButton } from './icons.jsx'

const ACCEPT = ['.pdf', '.md']

export default function UploadPanel({ onUploaded, onClose }) {
  const [file, setFile] = useState(null)
  const [topic, setTopic] = useState('SPRING')
  const [busy, setBusy] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [message, setMessage] = useState(null)
  const inputRef = useRef(null)

  function pick(f) {
    if (!f) return
    if (!ACCEPT.some((ext) => f.name.toLowerCase().endsWith(ext))) {
      setMessage({ ok: false, text: 'Chỉ hỗ trợ file .pdf và .md' })
      return
    }
    setMessage(null)
    setFile(f)
  }

  async function submit(e) {
    e.preventDefault()
    if (!file || busy) return
    setBusy(true)
    setMessage(null)
    try {
      const doc = await api.uploadDocument(file, topic)
      setMessage(
        doc.status === 'INDEXED'
          ? { ok: true, text: `Đã nạp ${doc.fileName} — ${doc.chunkCount} chunk` }
          : { ok: false, text: doc.errorMessage ?? `Trạng thái: ${doc.status}` },
      )
      setFile(null)
      if (inputRef.current) inputRef.current.value = ''
      onUploaded()
    } catch (err) {
      setMessage({ ok: false, text: err.message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="mx-3 mb-3 rounded-xl border border-line bg-canvas p-3 shadow-sm">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-medium">Nạp tài liệu</span>
        <IconButton label="Đóng" small onClick={onClose}>
          <XIcon className="h-4 w-4" />
        </IconButton>
      </div>

      <label
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          pick(e.dataTransfer.files[0])
        }}
        className={`flex cursor-pointer flex-col items-center gap-1 rounded-lg border border-dashed px-3 py-4 text-center text-sm transition ${
          dragging ? 'border-accent bg-accent-soft' : 'border-line-strong hover:bg-white'
        }`}
      >
        <UploadIcon className="h-5 w-5 text-muted" />
        {file ? (
          <span className="max-w-full truncate font-medium text-ink">{file.name}</span>
        ) : (
          <span className="text-ink-soft">
            Kéo thả hoặc <span className="text-accent underline-offset-2 hover:underline">chọn file</span>
          </span>
        )}
        <span className="text-xs text-muted">PDF hoặc Markdown · tối đa 20 MB</span>
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT.join(',')}
          className="sr-only"
          onChange={(e) => pick(e.target.files[0])}
        />
      </label>

      <div className="mt-2.5 flex items-center gap-2">
        <TopicSelect value={topic} onChange={setTopic} className="flex-1" />
        <button
          type="submit"
          disabled={!file || busy}
          className="rounded-lg bg-accent px-3.5 py-1.5 text-sm font-medium text-white transition hover:bg-accent-strong disabled:cursor-not-allowed disabled:bg-accent/40"
        >
          {busy ? 'Đang index…' : 'Nạp'}
        </button>
      </div>
      {busy && <p className="mt-2 text-xs text-muted">Đang đọc, cắt chunk và tạo embedding — file lớn có thể mất vài phút.</p>}
      {message && (
        <p className={`mt-2 text-xs ${message.ok ? 'text-success' : 'text-danger'}`}>{message.text}</p>
      )}
    </form>
  )
}
