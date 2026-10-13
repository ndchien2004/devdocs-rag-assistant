import { useEffect, useRef, useState } from 'react'
import { DEFAULT_CHAT_OPTIONS, RAG_MODES, TOP_K_MAX } from '../constants.js'
import { SlidersIcon } from './icons.jsx'

const isDefault = (o) => o.topK === DEFAULT_CHAT_OPTIONS.topK && o.mode === DEFAULT_CHAT_OPTIONS.mode

/**
 * Nút "Tùy chọn nâng cao" trong khung nhập: chọn topK và cách thực hiện RAG (để so sánh như thí nghiệm E5).
 * showMode = false: chỉ chọn topK (hội thoại đã lưu luôn dùng RAG thủ công — advisor không có phạm vi/lịch sử).
 */
export default function AdvancedOptions({ value, onChange, showMode = true }) {
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
        className={`chip relative grid h-[30px] w-[30px] place-items-center ${
          open || changed ? '!border-accent/40 text-accent' : 'text-ink-soft'
        }`}
      >
        <SlidersIcon className="h-4 w-4" />
        {changed && <span className="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full bg-accent" />}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Tùy chọn nâng cao"
          className="glass-strong absolute bottom-full left-0 z-30 mb-3 w-72 rounded-3xl p-4"
        >
          <label className="flex items-center justify-between gap-3 text-xs font-medium text-ink-soft">
            Số đoạn tìm kiếm (top-K)
            <select
              value={value.topK}
              onChange={(e) => onChange({ ...value, topK: e.target.value })}
              className="cursor-pointer rounded-full border border-line bg-white/80 px-3 py-1 text-xs text-ink focus:border-accent focus:outline-none"
            >
              <option value="">Mặc định</option>
              {Array.from({ length: TOP_K_MAX }, (_, i) => i + 1).map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
          </label>

          {showMode && (
          <fieldset className="mt-3.5">
            <legend className="mb-1.5 text-xs font-medium text-ink-soft">Cách thực hiện RAG</legend>
            <div className="space-y-1">
              {RAG_MODES.map((m) => (
                <label
                  key={m.value}
                  className={`flex cursor-pointer gap-2.5 rounded-2xl px-2.5 py-2 transition hover:bg-hover ${
                    value.mode === m.value ? 'bg-accent-soft/70' : ''
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
          )}

          {changed && (
            <button
              type="button"
              onClick={() => onChange(DEFAULT_CHAT_OPTIONS)}
              className="mt-3 text-xs font-medium text-accent-strong hover:underline"
            >
              Khôi phục mặc định
            </button>
          )}
        </div>
      )}
    </div>
  )
}
