import { useEffect, useRef, useState } from 'react'
import { DEFAULT_CHAT_OPTIONS, RAG_MODES, TOP_K_MAX } from '../constants.js'
import { SlidersIcon } from './icons.jsx'

const isDefault = (o) => o.topK === DEFAULT_CHAT_OPTIONS.topK && o.mode === DEFAULT_CHAT_OPTIONS.mode

/** Nút "Tùy chọn nâng cao" trong khung nhập: chọn topK và cách thực hiện RAG (để so sánh như thí nghiệm E5). */
export default function AdvancedOptions({ value, onChange }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef(null)
  const changed = !isDefault(value)

  useEffect(() => {
    if (!open) return
    const onPointerDown = (e) => {
      if (!rootRef.current?.contains(e.target)) setOpen(false)
    }
    const onKeyDown = (e) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label="Tùy chọn nâng cao"
        title="Tùy chọn nâng cao"
        className={`relative grid h-[30px] w-[30px] place-items-center rounded-lg border transition hover:border-line-strong ${
          open || changed ? 'border-accent/40 text-accent' : 'border-line text-ink-soft'
        } bg-white`}
      >
        <SlidersIcon className="h-4 w-4" />
        {changed && <span className="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full bg-accent" />}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Tùy chọn nâng cao"
          className="absolute bottom-full left-0 z-20 mb-2 w-72 rounded-xl border border-line-strong bg-white p-3.5 shadow-[0_8px_28px_rgba(31,30,29,0.12)]"
        >
          <label className="flex items-center justify-between gap-3 text-xs font-medium text-ink-soft">
            Số đoạn tìm kiếm (top-K)
            <select
              value={value.topK}
              onChange={(e) => onChange({ ...value, topK: e.target.value })}
              className="cursor-pointer rounded-lg border border-line bg-white px-2 py-1 text-xs text-ink focus:border-accent focus:outline-none"
            >
              <option value="">Mặc định</option>
              {Array.from({ length: TOP_K_MAX }, (_, i) => i + 1).map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
          </label>

          <fieldset className="mt-3.5">
            <legend className="mb-1.5 text-xs font-medium text-ink-soft">Cách thực hiện RAG</legend>
            <div className="space-y-1">
              {RAG_MODES.map((m) => (
                <label
                  key={m.value}
                  className={`flex cursor-pointer gap-2.5 rounded-lg px-2 py-1.5 transition hover:bg-panel ${
                    value.mode === m.value ? 'bg-panel' : ''
                  }`}
                >
                  <input
                    type="radio"
                    name="rag-mode"
                    value={m.value}
                    checked={value.mode === m.value}
                    onChange={() => onChange({ ...value, mode: m.value })}
                    className="mt-0.5 accent-[var(--color-accent)]"
                  />
                  <span>
                    <span className="block text-sm text-ink">{m.label}</span>
                    <span className="block text-xs leading-snug text-muted">{m.hint}</span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>

          {changed && (
            <button
              type="button"
              onClick={() => onChange(DEFAULT_CHAT_OPTIONS)}
              className="mt-2.5 text-xs text-accent hover:underline"
            >
              Khôi phục mặc định
            </button>
          )}
        </div>
      )}
    </div>
  )
}
