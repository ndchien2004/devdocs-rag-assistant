import { useLayoutEffect, useRef, useState } from 'react'
import TopicSelect from './TopicSelect.jsx'
import { ArrowUpIcon } from './icons.jsx'

const MAX_QUESTION = 1000
const MAX_HEIGHT = 220

/** Khung nhập câu hỏi: tự giãn chiều cao, Enter để gửi, Shift+Enter xuống dòng. */
export default function Composer({ onSend, disabled, topic, onTopicChange, autoFocus = false }) {
  const [value, setValue] = useState('')
  const ref = useRef(null)

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`
  }, [value])

  function submit(e) {
    e?.preventDefault()
    const q = value.trim()
    if (!q || disabled) return
    onSend(q)
    setValue('')
  }

  return (
    <form
      onSubmit={submit}
      className="rounded-2xl border border-line-strong bg-white px-3.5 pt-3 pb-2.5 shadow-[0_2px_12px_rgba(31,30,29,0.05)] transition focus-within:border-[#c9c5b6] focus-within:shadow-[0_2px_16px_rgba(31,30,29,0.08)]"
    >
      <textarea
        ref={ref}
        value={value}
        onChange={(e) => setValue(e.target.value.slice(0, MAX_QUESTION))}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) submit(e)
        }}
        rows={1}
        autoFocus={autoFocus}
        placeholder="Hỏi bất cứ điều gì về tài liệu của bạn…"
        aria-label="Câu hỏi"
        className="block max-h-[220px] w-full resize-none bg-transparent px-0.5 text-[15px] leading-6 text-ink placeholder:text-muted focus:outline-none"
      />
      <div className="mt-2.5 flex items-center justify-between gap-2">
        <TopicSelect value={topic} onChange={onTopicChange} allLabel="Tất cả chủ đề" />
        <div className="flex items-center gap-3">
          {value.length > MAX_QUESTION * 0.8 && (
            <span className="text-xs text-muted tabular-nums">
              {value.length}/{MAX_QUESTION}
            </span>
          )}
          <button
            type="submit"
            disabled={disabled || !value.trim()}
            aria-label="Gửi"
            title="Gửi (Enter)"
            className="grid h-8 w-8 place-items-center rounded-lg bg-accent text-white transition hover:bg-accent-strong disabled:cursor-not-allowed disabled:bg-accent/35"
          >
            <ArrowUpIcon className="h-4 w-4" />
          </button>
        </div>
      </div>
    </form>
  )
}
