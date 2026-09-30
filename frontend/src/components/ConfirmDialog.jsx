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
      className="m-auto w-full max-w-md rounded-xl p-0 shadow-xl backdrop:bg-slate-900/40"
    >
      <div className="p-5">
        <h2 className="text-lg font-semibold">{title}</h2>
        <p className="mt-2 text-sm text-slate-600">{message}</p>
      </div>
      <div className="flex justify-end gap-2 border-t border-slate-100 bg-slate-50 px-5 py-3">
        <button onClick={onCancel} className="rounded-md px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-200">
          Hủy
        </button>
        <button
          onClick={onConfirm}
          className={`rounded-md px-3 py-1.5 text-sm font-medium text-white ${
            danger ? 'bg-red-600 hover:bg-red-700' : 'bg-sky-600 hover:bg-sky-700'
          }`}
        >
          {confirmLabel}
        </button>
      </div>
    </dialog>
  )
}
