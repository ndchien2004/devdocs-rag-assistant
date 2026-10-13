const STATUS = {
  INDEXED: { color: 'bg-success', label: 'Đã index' },
  PROCESSING: { color: 'bg-warn animate-pulse', label: 'Đang xử lý' },
  PENDING: { color: 'bg-warn', label: 'Chờ xử lý' },
  FAILED: { color: 'bg-danger', label: 'Lỗi' },
}

export const statusLabel = (status) => STATUS[status]?.label ?? status

export default function StatusDot({ status, title }) {
  const s = STATUS[status] ?? STATUS.PENDING
  return (
    <span
      title={title ?? s.label}
      aria-label={s.label}
      className={`inline-block h-2 w-2 shrink-0 rounded-full ring-2 ring-white/80 ${s.color}`}
    />
  )
}
