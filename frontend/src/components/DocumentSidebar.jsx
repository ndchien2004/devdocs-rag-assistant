import { useState } from 'react'
import { api } from '../api/client.js'
import { topicLabel } from '../constants.js'
import ConfirmDialog from './ConfirmDialog.jsx'
import StatusDot, { statusLabel } from './StatusDot.jsx'
import UploadPanel from './UploadPanel.jsx'
import {
  BookIcon,
  ExpandIcon,
  FilesIcon,
  IconButton,
  PanelLeftIcon,
  PlusIcon,
  RefreshIcon,
  ShrinkIcon,
  TrashIcon,
} from './icons.jsx'

const formatDate = (iso) =>
  iso
    ? new Date(iso).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })
    : '—'

function FileBadge({ fileName }) {
  const pdf = fileName.toLowerCase().endsWith('.pdf')
  return (
    <span
      className={`grid h-8 w-8 shrink-0 place-items-center rounded-md text-[9px] font-semibold tracking-wide ${
        pdf ? 'bg-accent-soft text-accent-strong' : 'bg-bubble text-ink-soft'
      }`}
    >
      {pdf ? 'PDF' : 'MD'}
    </span>
  )
}

function RowActions({ doc, busy, onAction, alwaysVisible }) {
  if (busy) {
    return <span className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-line-strong border-t-accent" />
  }
  return (
    <div className={`flex shrink-0 ${alwaysVisible ? '' : 'opacity-0 group-hover:opacity-100 focus-within:opacity-100'}`}>
      <IconButton small label={`Index lại ${doc.fileName}`} onClick={() => onAction('reindex', doc)}>
        <RefreshIcon className="h-4 w-4" />
      </IconButton>
      <IconButton
        small
        label={`Xóa ${doc.fileName}`}
        onClick={() => onAction('delete', doc)}
        className="hover:!text-danger"
      >
        <TrashIcon className="h-4 w-4" />
      </IconButton>
    </div>
  )
}

/** Chế độ "open": danh sách gọn. */
function DocumentList({ documents, busyId, onAction }) {
  return (
    <ul className="space-y-0.5">
      {documents.map((d) => (
        <li
          key={d.id}
          data-role="document-row"
          className="group flex items-center gap-2.5 rounded-lg px-2 py-1.5 transition hover:bg-hover"
        >
          <FileBadge fileName={d.fileName} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm text-ink" title={d.fileName}>
              {d.fileName}
            </p>
            <p className="flex items-center gap-1.5 text-xs text-muted">
              <StatusDot status={d.status} title={d.errorMessage ?? undefined} />
              <span className="truncate">
                {topicLabel(d.topic)}
                {d.status === 'INDEXED' ? ` · ${d.chunkCount} chunk` : ` · ${statusLabel(d.status)}`}
              </span>
            </p>
          </div>
          <RowActions doc={d} busy={busyId === d.id} onAction={onAction} />
        </li>
      ))}
    </ul>
  )
}

/** Chế độ "expanded": bảng đầy đủ. */
function DocumentTable({ documents, busyId, onAction }) {
  return (
    <table className="w-full table-fixed text-sm">
      <colgroup>
        <col />
        <col className="w-[5rem]" />
        <col className="w-[6rem]" />
        <col className="w-[3rem]" />
        <col className="w-[3.5rem]" />
        <col className="w-[6.25rem]" />
        <col className="w-[4.25rem]" />
      </colgroup>
      <thead>
        <tr className="border-b border-line text-left text-xs whitespace-nowrap text-muted">
          <th className="py-2 pr-2 pl-2 font-medium">Tên file</th>
          <th className="px-2 py-2 font-medium">Chủ đề</th>
          <th className="px-2 py-2 font-medium">Trạng thái</th>
          <th className="px-2 py-2 text-right font-medium">Trang</th>
          <th className="px-2 py-2 text-right font-medium">Chunk</th>
          <th className="px-2 py-2 font-medium">Index lúc</th>
          <th />
        </tr>
      </thead>
      <tbody>
        {documents.map((d) => (
          <tr key={d.id} data-role="document-row" className="group border-b border-line/70 transition hover:bg-hover/60">
            <td className="py-2 pr-2 pl-2">
              <div className="flex min-w-0 items-center gap-2.5">
                <FileBadge fileName={d.fileName} />
                <span className="truncate" title={d.fileName}>
                  {d.fileName}
                </span>
              </div>
            </td>
            <td className="truncate px-2 py-2 text-ink-soft">{topicLabel(d.topic)}</td>
            <td className="px-2 py-2 whitespace-nowrap">
              <span className="inline-flex items-center gap-1.5 text-ink-soft" title={d.errorMessage ?? undefined}>
                <StatusDot status={d.status} />
                {statusLabel(d.status)}
              </span>
            </td>
            <td className="px-2 py-2 text-right text-ink-soft tabular-nums">{d.pageCount ?? '—'}</td>
            <td className="px-2 py-2 text-right text-ink-soft tabular-nums">{d.chunkCount ?? '—'}</td>
            <td className="px-2 py-2 whitespace-nowrap text-muted">{formatDate(d.indexedAt)}</td>
            <td className="py-2 pr-1">
              <RowActions doc={d} busy={busyId === d.id} onAction={onAction} alwaysVisible />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default function DocumentSidebar({ mode, onModeChange, documents, loading, error, onChanged }) {
  const [showUpload, setShowUpload] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const [confirm, setConfirm] = useState(null) // { action: 'delete' | 'reindex', doc }
  const [actionError, setActionError] = useState(null)

  async function runConfirmed() {
    const { action, doc } = confirm
    setConfirm(null)
    setBusyId(doc.id)
    setActionError(null)
    try {
      if (action === 'delete') await api.deleteDocument(doc.id)
      else await api.reindexDocument(doc.id)
      await onChanged()
    } catch (err) {
      setActionError(err.message)
    } finally {
      setBusyId(null)
    }
  }

  const dialog = (
    <ConfirmDialog
      open={confirm !== null}
      title={confirm?.action === 'delete' ? 'Xóa tài liệu?' : 'Index lại tài liệu?'}
      message={
        confirm?.action === 'delete'
          ? `"${confirm?.doc.fileName}" và toàn bộ chunk của nó sẽ bị xóa. Không thể hoàn tác.`
          : `Chunk cũ của "${confirm?.doc.fileName}" sẽ bị xóa và tạo lại từ file gốc.`
      }
      confirmLabel={confirm?.action === 'delete' ? 'Xóa' : 'Index lại'}
      danger={confirm?.action === 'delete'}
      onConfirm={runConfirmed}
      onCancel={() => setConfirm(null)}
    />
  )

  // --- Thu gọn: dải icon mảnh
  if (mode === 'collapsed') {
    return (
      <aside className="flex w-14 shrink-0 flex-col items-center gap-1.5 border-r border-line bg-panel py-3">
        <IconButton label="Mở thanh tài liệu" onClick={() => onModeChange('open')}>
          <PanelLeftIcon />
        </IconButton>
        <IconButton
          label="Nạp tài liệu"
          onClick={() => {
            setShowUpload(true)
            onModeChange('open')
          }}
        >
          <PlusIcon />
        </IconButton>
        <IconButton label={`${documents.length} tài liệu`} onClick={() => onModeChange('open')} className="relative">
          <FilesIcon />
          {documents.length > 0 && (
            <span className="absolute -top-0.5 -right-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-accent px-1 text-[10px] font-semibold text-white">
              {documents.length}
            </span>
          )}
        </IconButton>
        {dialog}
      </aside>
    )
  }

  const expanded = mode === 'expanded'
  return (
    <aside
      className={`flex shrink-0 flex-col border-r border-line bg-panel transition-[width] duration-300 ease-out ${
        expanded ? 'w-1/2 min-w-[26rem]' : 'w-72'
      }`}
    >
      <div className="flex h-14 shrink-0 items-center justify-between px-3">
        <div className="flex items-center gap-2 pl-1">
          <BookIcon className="h-5 w-5 text-accent" />
          <span className="font-serif text-[17px] font-semibold tracking-tight">DevDocs</span>
        </div>
        <IconButton label="Thu gọn thanh bên" onClick={() => onModeChange('collapsed')}>
          <PanelLeftIcon />
        </IconButton>
      </div>

      <div className="flex items-center justify-between px-4 pt-1 pb-2">
        <span className="text-xs font-medium text-muted">
          Tài liệu {documents.length > 0 && <span className="text-ink-soft">· {documents.length}</span>}
        </span>
        <div className="flex gap-0.5">
          <IconButton small label="Nạp tài liệu" onClick={() => setShowUpload((v) => !v)}>
            <PlusIcon className="h-4 w-4" />
          </IconButton>
          <IconButton
            small
            label={expanded ? 'Thu nhỏ danh sách' : 'Mở rộng danh sách'}
            onClick={() => onModeChange(expanded ? 'open' : 'expanded')}
          >
            {expanded ? <ShrinkIcon className="h-4 w-4" /> : <ExpandIcon className="h-4 w-4" />}
          </IconButton>
        </div>
      </div>

      {showUpload && <UploadPanel onUploaded={onChanged} onClose={() => setShowUpload(false)} />}

      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        {loading && <p className="px-2 py-3 text-sm text-muted">Đang tải…</p>}
        {!loading && documents.length === 0 && !error && (
          <div className="px-2 py-6 text-center text-sm text-muted">
            <p>Chưa có tài liệu nào.</p>
            <button onClick={() => setShowUpload(true)} className="mt-1 text-accent hover:underline">
              Nạp tài liệu đầu tiên
            </button>
          </div>
        )}
        {!loading && documents.length > 0 &&
          (expanded ? (
            <DocumentTable documents={documents} busyId={busyId} onAction={(action, doc) => setConfirm({ action, doc })} />
          ) : (
            <DocumentList documents={documents} busyId={busyId} onAction={(action, doc) => setConfirm({ action, doc })} />
          ))}
      </div>

      {(error || actionError) && (
        <p className="border-t border-line px-4 py-2.5 text-xs text-danger">{actionError ?? error}</p>
      )}
      {dialog}
    </aside>
  )
}
