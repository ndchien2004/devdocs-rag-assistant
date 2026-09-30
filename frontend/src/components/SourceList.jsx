import { useState } from 'react'

export const sourceElementId = (messageId, index) => `src-${messageId}-${index}`

function location(source) {
  if (source.sectionTitle) return `mục ${source.pageNumber}: ${source.sectionTitle}`
  return `trang ${source.pageNumber}`
}

function SourceItem({ messageId, source }) {
  const [open, setOpen] = useState(false)
  return (
    <li id={sourceElementId(messageId, source.index)} className="rounded px-2 py-1.5">
      <div className="flex flex-wrap items-baseline gap-x-2 text-sm">
        <span className="font-semibold text-sky-700">[{source.index}]</span>
        <span className="font-medium">{source.fileName}</span>
        <span className="text-slate-500">· {location(source)}</span>
        {source.score != null && <span className="text-slate-400">· {source.score.toFixed(2)}</span>}
        <button onClick={() => setOpen(!open)} className="ml-auto text-xs text-sky-700 hover:underline">
          {open ? '▾ ẩn đoạn trích' : '▸ xem đoạn trích'}
        </button>
      </div>
      {open && <p className="mt-1 border-l-2 border-slate-200 pl-3 text-sm text-slate-600">{source.snippet}</p>}
    </li>
  )
}

export default function SourceList({ messageId, sources }) {
  if (!sources?.length) return null
  return (
    <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-2">
      <div className="px-2 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Nguồn</div>
      <ul>
        {sources.map((s) => (
          <SourceItem key={s.index} messageId={messageId} source={s} />
        ))}
      </ul>
    </div>
  )
}
