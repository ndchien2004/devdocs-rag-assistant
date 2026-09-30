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
      className="m-auto w-full max-w-sm rounded-2xl border border-line bg-canvas p-0 text-ink shadow-2xl backdrop:bg-ink/30 backdrop:backdrop-blur-[2px]"
    >
      <div className="px-5 pt-5 pb-4">
        <h2 className="font-serif text-lg font-semibold">{title}</h2>
        <p className="mt-2 text-sm leading-relaxed text-ink-soft">{message}</p>
      </div>
      <div className="flex justify-end gap-2 px-5 pb-5">
        <button
          onClick={onCancel}
          className="rounded-lg border border-line-strong bg-white px-3.5 py-1.5 text-sm text-ink-soft transition hover:text-ink"
        >
          Hủy
        </button>
        <button
          onClick={onConfirm}
          className={`rounded-lg px-3.5 py-1.5 text-sm font-medium text-white transition ${
            danger ? 'bg-danger hover:bg-danger/90' : 'bg-accent hover:bg-accent-strong'
          }`}
        >
          {confirmLabel}
        </button>
      </div>
    </dialog>
  )
}
