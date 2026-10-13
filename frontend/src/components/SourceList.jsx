export const sourceElementId = (messageId, index) => `src-${messageId}-${index}`

function location(source, short = false) {
  if (source.sectionTitle) return short ? `mục ${source.pageNumber}` : `mục ${source.pageNumber}: ${source.sectionTitle}`
  return short ? `tr. ${source.pageNumber}` : `trang ${source.pageNumber}`
}

/** Nguồn dạng "pill"; bấm để xem đoạn trích (mỗi lúc một nguồn). */
export default function SourceList({ messageId, sources, openIndex, onToggle, onOpenPdf, availableDocumentIds = [] }) {
  if (!sources?.length) return null
  const open = sources.find((s) => s.index === openIndex)

  return (
    <div className="mt-4">
      <div className="flex flex-wrap gap-1.5">
        {sources.map((s) => {
          const active = s.index === openIndex
          return (
            <button
              key={s.index}
              id={sourceElementId(messageId, s.index)}
              type="button"
              onClick={() => onToggle(active ? null : s.index)}
              aria-expanded={active}
              className={`chip inline-flex max-w-full items-center gap-1.5 px-3 py-1 text-xs ${
                active ? '!border-accent/45 !bg-accent-soft/90 text-ink' : 'text-ink-soft hover:text-ink'
              }`}
            >
              <span className="font-semibold text-accent-strong">{s.index}</span>
              <span className="truncate">{s.fileName}</span>
              <span className="shrink-0 text-muted">· {location(s, true)}</span>
            </button>
          )
        })}
      </div>

      {open && (
        <div data-role="source-snippet" className="mt-2.5 rounded-2xl border border-white/80 bg-white/70 px-4 py-3 text-sm shadow-[inset_0_1px_0_#fff]">
          <div className="mb-1.5 flex flex-wrap items-baseline justify-between gap-x-3 text-xs text-muted">
            <span>
              <span className="font-medium text-ink-soft">{open.fileName}</span> — {location(open)}
            </span>
            {open.score != null && <span>độ liên quan {open.score.toFixed(2)}</span>}
          </div>
          <p className="leading-relaxed text-ink-soft">{open.snippet}</p>
          {open.documentId && open.fileName?.toLowerCase().endsWith('.pdf') && (availableDocumentIds.includes(open.documentId)
            ? <button type="button" onClick={() => onOpenPdf?.(open)} className="mt-2.5 rounded-full bg-accent-soft px-3 py-1 text-xs font-medium text-accent-strong transition hover:bg-accent hover:text-white">Mở PDF tại {location(open)}</button>
            : <p className="mt-2 text-xs text-muted">PDF này đã bị xóa khỏi thư viện; trích dẫn được giữ trong lịch sử.</p>)}
        </div>
      )}
    </div>
  )
}
