const STYLES = {
  INDEXED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  PROCESSING: 'bg-amber-50 text-amber-700 ring-amber-200',
  PENDING: 'bg-slate-100 text-slate-600 ring-slate-200',
  FAILED: 'bg-red-50 text-red-700 ring-red-200',
}

export default function StatusBadge({ status, title }) {
  return (
    <span
      title={title}
      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ring-1 ${STYLES[status] ?? STYLES.PENDING}`}
    >
      {status}
    </span>
  )
}
