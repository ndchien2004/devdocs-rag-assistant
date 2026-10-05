import { useEffect, useRef, useState } from 'react'
import { GlobalWorkerOptions, getDocument } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { XIcon } from './icons.jsx'

GlobalWorkerOptions.workerSrc = workerUrl

export default function PdfViewer({ file, url, fileName, fileSize, initialPage = 1, onPageCount, onClose }) {
  const canvasRef = useRef(null)
  const stageRef = useRef(null)
  const [pdf, setPdf] = useState(null)
  const [page, setPage] = useState(initialPage || 1)
  const [pages, setPages] = useState(0)
  const [zoom, setZoom] = useState(1)
  const [width, setWidth] = useState(800)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => setPage(Math.max(1, initialPage || 1)), [initialPage, file, url])
  useEffect(() => {
    let alive = true
    let task
    setLoading(true); setError(null); setPdf(null)
    ;(async () => {
      try {
        let data
        if (file) data = await file.arrayBuffer()
        else {
          const response = await fetch(url)
          if (!response.ok) throw new Error('PDF unavailable')
          data = await response.arrayBuffer()
        }
        task = getDocument({ data })
        const loaded = await task.promise
        if (!alive) { await loaded.destroy(); return }
        setPdf(loaded); setPages(loaded.numPages); onPageCount?.(loaded.numPages)
        setPage((n) => Math.min(Math.max(1, initialPage || n), loaded.numPages))
      } catch (err) {
        if (!alive) return
        const msg = String(err?.message ?? '').toLowerCase()
        setError(msg.includes('password')
          ? 'PDF được bảo vệ bằng mật khẩu. Hãy gỡ mật khẩu rồi thử lại.'
          : 'Không thể mở PDF. File có thể bị hỏng hoặc không hợp lệ.')
      } finally { if (alive) setLoading(false) }
    })()
    return () => { alive = false; task?.destroy() }
  }, [file, url, initialPage, onPageCount])

  useEffect(() => {
    const element = stageRef.current
    if (!element) return
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    if (!pdf || !canvasRef.current) return
    let renderTask
    let alive = true
    ;(async () => {
      try {
        const current = await pdf.getPage(page)
        const base = current.getViewport({ scale: 1 })
        const scale = Math.max(0.35, (width - 24) / base.width) * zoom
        const viewport = current.getViewport({ scale })
        const ratio = window.devicePixelRatio || 1
        const canvas = canvasRef.current
        canvas.width = Math.floor(viewport.width * ratio)
        canvas.height = Math.floor(viewport.height * ratio)
        canvas.style.width = `${viewport.width}px`
        canvas.style.height = `${viewport.height}px`
        renderTask = current.render({ canvas, canvasContext: canvas.getContext('2d'), viewport, transform: ratio !== 1 ? [ratio, 0, 0, ratio, 0, 0] : null })
        await renderTask.promise
      } catch (err) { if (alive && err?.name !== 'RenderingCancelledException') setError('Không thể hiển thị trang PDF này.') }
    })()
    return () => { alive = false; renderTask?.cancel() }
  }, [pdf, page, zoom, width])

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-2 sm:p-6" role="dialog" aria-modal="true" aria-label="Xem PDF" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
    <div className="flex h-full max-h-[94vh] w-full max-w-5xl flex-col overflow-hidden rounded-xl bg-canvas shadow-2xl">
      <header className="flex min-h-14 items-center justify-between gap-3 border-b border-line px-3 sm:px-5">
        <div className="min-w-0"><p className="truncate text-sm font-medium">{fileName ?? file?.name ?? 'Tài liệu PDF'}</p><p className="text-xs text-muted">{fileSize || file?.size ? `${((fileSize ?? file.size) / (1024 * 1024)).toFixed(2)} MB · ` : ''}{pages ? `${pages} trang` : 'Đang đọc PDF…'}</p></div>
        <div className="flex shrink-0 items-center gap-1.5 text-sm">
          <button type="button" disabled={page <= 1} onClick={() => setPage((n) => Math.max(1, n - 1))} aria-label="Trang trước" className="rounded-md border border-line px-2 py-1 disabled:opacity-40">‹</button>
          <span className="min-w-16 text-center tabular-nums">{page} / {pages || '—'}</span>
          <button type="button" disabled={!pages || page >= pages} onClick={() => setPage((n) => Math.min(pages, n + 1))} aria-label="Trang sau" className="rounded-md border border-line px-2 py-1 disabled:opacity-40">›</button>
          <button type="button" onClick={() => setZoom((n) => Math.max(0.6, +(n - 0.15).toFixed(2)))} aria-label="Thu nhỏ" className="ml-1 rounded-md border border-line px-2 py-1">−</button>
          <span className="w-10 text-center text-xs">{Math.round(zoom * 100)}%</span>
          <button type="button" onClick={() => setZoom((n) => Math.min(2.5, +(n + 0.15).toFixed(2)))} aria-label="Phóng to" className="rounded-md border border-line px-2 py-1">+</button>
          <button type="button" onClick={onClose} aria-label="Đóng PDF" className="ml-1 rounded-md p-1.5 hover:bg-hover"><XIcon className="h-4 w-4" /></button>
        </div>
      </header>
      <div ref={stageRef} className="min-h-0 flex-1 overflow-auto p-3 sm:p-5">
        {loading && <p role="status" className="py-8 text-center text-sm text-muted">Đang mở PDF…</p>}
        {error && <p role="alert" className="mx-auto mt-8 max-w-md rounded-lg border border-danger/25 bg-danger/5 p-4 text-sm text-danger">{error}</p>}
        {!loading && !error && <canvas ref={canvasRef} className="mx-auto shadow-md" />}
      </div>
    </div>
  </div>
}
