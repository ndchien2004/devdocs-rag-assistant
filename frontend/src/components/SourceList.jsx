export const sourceElementId = (messageId, index) => `src-${messageId}-${index}`

function location(source, short = false) {
  if (source.sectionTitle) return short ? `mục ${source.pageNumber}` : `mục ${source.pageNumber}: ${source.sectionTitle}`
  return short ? `tr. ${source.pageNumber}` : `trang ${source.pageNumber}`
}

/** Nguồn dạng "pill"; bấm để xem đoạn trích (mỗi lúc một nguồn). */
export default function SourceList({ messageId, sources, openIndex, onToggle }) {
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
              className={`inline-flex max-w-full items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs transition ${
                active
                  ? 'border-accent/40 bg-accent-soft text-ink'
                  : 'border-line bg-white text-ink-soft hover:border-line-strong hover:text-ink'
              }`}
            >
              <span className="font-semibold text-accent">{s.index}</span>
              <span className="truncate">{s.fileName}</span>
              <span className="shrink-0 text-muted">· {location(s, true)}</span>
            </button>
          )
        })}
      </div>

      {open && (
        <div data-role="source-snippet" className="mt-2 rounded-xl border border-line bg-white px-3.5 py-3 text-sm">
          <div className="mb-1.5 flex flex-wrap items-baseline justify-between gap-x-3 text-xs text-muted">
            <span>
              <span className="font-medium text-ink-soft">{open.fileName}</span> — {location(open)}
            </span>
            {open.score != null && <span>độ liên quan {open.score.toFixed(2)}</span>}
          </div>
          <p className="leading-relaxed text-ink-soft">{open.snippet}</p>
        </div>
      )}
    </div>
  )
}
