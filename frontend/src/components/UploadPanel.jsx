import { lazy, Suspense, useRef, useState } from 'react'
import { api } from '../api/client.js'
import TopicSelect from './TopicSelect.jsx'
import { LoadingOverlay } from './Overlay.jsx'
import { UploadIcon, XIcon, IconButton } from './icons.jsx'
const PdfViewer = lazy(() => import('./PdfViewer.jsx'))

const ACCEPT = ['.pdf', '.md']

export default function UploadPanel({ onUploaded, onClose }) {
  const [file, setFile] = useState(null)
  const [topic, setTopic] = useState('SPRING')
  const [busy, setBusy] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [message, setMessage] = useState(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [pageCount, setPageCount] = useState(null)
  const inputRef = useRef(null)

  function pick(f) {
    if (!f) return
    if (!ACCEPT.some((ext) => f.name.toLowerCase().endsWith(ext))) {
      setMessage({ ok: false, text: 'Chỉ hỗ trợ file .pdf và .md' })
      return
    }
    if (f.size > 20 * 1024 * 1024) {
      setMessage({ ok: false, text: 'File vượt quá giới hạn 20 MB. Hãy chọn file nhỏ hơn.' })
      return
    }
    setMessage(null)
    setFile(f)
    setPageCount(null)
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
      if (doc.status === 'INDEXED') setFile(null)
      if (inputRef.current) inputRef.current.value = ''
      onUploaded()
    } catch (err) {
      setMessage({ ok: false, text: err.message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="mx-3 mb-3 rounded-3xl border border-white/80 bg-white/55 p-3.5 shadow-[0_4px_20px_rgba(15,23,42,0.06),inset_0_1px_0_#fff]">
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
        className={`flex cursor-pointer flex-col items-center gap-1 rounded-2xl border-[1.5px] border-dashed px-3 py-5 text-center text-sm transition ${
          dragging
            ? 'border-accent bg-accent-soft/80 shadow-[0_0_0_4px_rgba(34,211,238,0.15)]'
            : 'border-accent/30 bg-white/40 hover:border-accent/50 hover:bg-white/70'
        }`}
      >
        <UploadIcon className="h-5 w-5 text-accent" />
        {file ? (
          <span className="max-w-full truncate font-medium text-ink">{file.name}</span>
        ) : (
          <span className="text-ink-soft">
            Kéo thả hoặc <span className="font-medium text-accent-strong underline-offset-2 hover:underline">chọn file</span>
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

      {file && <div className="mt-2 flex items-center justify-between gap-2 text-xs text-muted">
        <span className="truncate">{(file.size / (1024 * 1024)).toFixed(2)} MB{pageCount ? ` · ${pageCount} trang` : ''}</span>
        {file.name.toLowerCase().endsWith('.pdf') && <button type="button" onClick={() => setPreviewOpen(true)} className="shrink-0 rounded-full bg-accent-soft px-2.5 py-0.5 font-medium text-accent-strong transition hover:bg-accent hover:text-white">Xem trước PDF</button>}
      </div>}

      <div className="mt-2.5 flex items-center gap-2">
        <TopicSelect value={topic} onChange={setTopic} className="flex-1" />
        <button
          type="submit"
          disabled={!file || busy}
          className="btn-cyan px-4 py-1.5 text-sm font-medium"
        >
          {busy ? 'Đang xử lý…' : 'Nạp'}
        </button>
      </div>
      {busy && <p className="mt-2 text-xs text-muted" role="status">Đang tải lên, đọc tài liệu và tạo embedding. File lớn có thể mất vài phút.</p>}
      {message && (
        <p className={`mt-2 text-xs ${message.ok ? 'text-success' : 'text-danger'}`}>{message.text}</p>
      )}
      {previewOpen && file && <Suspense fallback={<LoadingOverlay>Đang tải trình xem PDF…</LoadingOverlay>}><PdfViewer file={file} fileName={file.name} initialPage={1} onPageCount={setPageCount} onClose={() => setPreviewOpen(false)} /></Suspense>}
    </form>
  )
}
