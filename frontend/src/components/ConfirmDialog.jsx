import { useEffect, useRef } from 'react'

/** Hộp thoại xác nhận dùng thẻ <dialog> gốc của trình duyệt (Esc để đóng). */
export default function ConfirmDialog({ open, title, message, confirmLabel, danger, onConfirm, onCancel }) {
  const ref = useRef(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onCancel={onCancel}
      className="glass-strong m-auto w-full max-w-sm rounded-3xl p-0 text-ink backdrop:bg-slate-900/15 backdrop:backdrop-blur-sm"
    >
      <div className="px-5 pt-5 pb-4">
        <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
        <p className="mt-2 text-sm leading-relaxed text-ink-soft">{message}</p>
      </div>
      <div className="flex justify-end gap-2 px-5 pb-5">
        <button
          onClick={onCancel}
          className="rounded-full border border-line bg-white/70 px-4 py-1.5 text-sm text-ink-soft transition hover:bg-white hover:text-ink"
        >
          Hủy
        </button>
        <button
          onClick={onConfirm}
          className={`px-4 py-1.5 text-sm font-medium ${
            danger
              ? 'rounded-full bg-gradient-to-br from-rose-500 to-rose-600 text-white shadow-[0_6px_18px_rgba(225,29,72,0.28),inset_0_1px_0_rgba(255,255,255,0.35)] transition hover:-translate-y-px'
              : 'btn-cyan'
          }`}
        >
          {confirmLabel}
        </button>
      </div>
    </dialog>
  )
}
