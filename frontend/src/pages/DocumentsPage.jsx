import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client.js'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import TopicSelect from '../components/TopicSelect.jsx'
import { topicLabel } from '../constants.js'

const formatDate = (iso) => (iso ? new Date(iso).toLocaleString('vi-VN') : '—')

function UploadForm({ onUploaded }) {
  const [file, setFile] = useState(null)
  const [topic, setTopic] = useState('SPRING')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState(null)
  const inputRef = useRef(null)

  async function submit(e) {
    e.preventDefault()
    if (!file) return
    setBusy(true)
    setMessage(null)
    try {
      const doc = await api.uploadDocument(file, topic)
      setMessage(
        doc.status === 'INDEXED'
          ? { ok: true, text: `Đã index ${doc.fileName}: ${doc.pageCount} trang, ${doc.chunkCount} chunk.` }
          : { ok: false, text: `${doc.fileName}: ${doc.status} — ${doc.errorMessage ?? ''}` },
      )
      setFile(null)
      inputRef.current.value = ''
      onUploaded()
    } catch (err) {
      setMessage({ ok: false, text: err.message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="mb-3 font-semibold">Nạp tài liệu</h2>
      <div className="flex flex-wrap items-center gap-2">
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.md"
          onChange={(e) => setFile(e.target.files[0] ?? null)}
          className="flex-1 text-sm file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm hover:file:bg-slate-200"
        />
        <TopicSelect value={topic} onChange={setTopic} />
        <button
          type="submit"
          disabled={!file || busy}
          className="rounded-md bg-sky-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-sky-700 disabled:cursor-not-allowed disabled:bg-slate-300"
        >
          {busy ? 'Đang index...' : 'Upload'}
        </button>
      </div>
      <p className="mt-2 text-xs text-slate-400">PDF hoặc Markdown, tối đa 20 MB. Index đồng bộ — file lớn có thể mất vài phút.</p>
      {message && (
        <p className={`mt-2 text-sm ${message.ok ? 'text-emerald-700' : 'text-red-600'}`}>{message.text}</p>
      )}
    </form>
  )
}

export default function DocumentsPage() {
  const [documents, setDocuments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [confirm, setConfirm] = useState(null) // { action: 'delete' | 'reindex', doc }

  const load = useCallback(async () => {
    try {
      setDocuments(await api.listDocuments())
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function runConfirmed() {
    const { action, doc } = confirm
    setConfirm(null)
    setBusyId(doc.id)
    try {
      if (action === 'delete') await api.deleteDocument(doc.id)
      else await api.reindexDocument(doc.id)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="mx-auto h-full max-w-4xl space-y-4 overflow-y-auto px-4 py-4">
      <UploadForm onUploaded={load} />

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-2">Tên file</th>
              <th className="px-2 py-2">Chủ đề</th>
              <th className="px-2 py-2">Trạng thái</th>
              <th className="px-2 py-2 text-right">Trang</th>
              <th className="px-2 py-2 text-right">Chunk</th>
              <th className="px-2 py-2">Ngày index</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading && (
              <tr>
                <td colSpan={7} className="px-4 py-6 text-center text-slate-400">Đang tải...</td>
              </tr>
            )}
            {!loading && documents.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-6 text-center text-slate-400">Chưa có tài liệu nào.</td>
              </tr>
            )}
            {documents.map((d) => (
              <tr key={d.id} className={busyId === d.id ? 'opacity-50' : ''}>
                <td className="px-4 py-2 font-medium">{d.fileName}</td>
                <td className="px-2 py-2">{topicLabel(d.topic)}</td>
                <td className="px-2 py-2">
                  <StatusBadge status={d.status} title={d.errorMessage ?? undefined} />
                </td>
                <td className="px-2 py-2 text-right tabular-nums">{d.pageCount ?? '—'}</td>
                <td className="px-2 py-2 text-right tabular-nums">{d.chunkCount ?? '—'}</td>
                <td className="px-2 py-2 text-slate-500">{formatDate(d.indexedAt)}</td>
                <td className="whitespace-nowrap px-4 py-2 text-right">
                  <button
                    disabled={busyId !== null}
                    onClick={() => setConfirm({ action: 'reindex', doc: d })}
                    className="rounded px-2 py-1 text-sky-700 hover:bg-sky-50 disabled:opacity-40"
                  >
                    Re-index
                  </button>
                  <button
                    disabled={busyId !== null}
                    onClick={() => setConfirm({ action: 'delete', doc: d })}
                    className="rounded px-2 py-1 text-red-600 hover:bg-red-50 disabled:opacity-40"
                  >
                    Xóa
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {error && <p className="text-sm text-red-600">⚠️ {error}</p>}

      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.action === 'delete' ? 'Xóa tài liệu?' : 'Index lại tài liệu?'}
        message={
          confirm?.action === 'delete'
            ? `"${confirm?.doc.fileName}" và toàn bộ chunk của nó sẽ bị xóa khỏi hệ thống.`
            : `Chunk cũ của "${confirm?.doc.fileName}" sẽ bị xóa và tạo lại từ file gốc.`
        }
        confirmLabel={confirm?.action === 'delete' ? 'Xóa' : 'Re-index'}
        danger={confirm?.action === 'delete'}
        onConfirm={runConfirmed}
        onCancel={() => setConfirm(null)}
      />
    </div>
  )
}
