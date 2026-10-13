import { useLayoutEffect, useRef, useState } from 'react'
import AdvancedOptions from './AdvancedOptions.jsx'
import TopicSelect from './TopicSelect.jsx'
import { ArrowUpIcon, StopIcon } from './icons.jsx'

const MAX_QUESTION = 1000
const MAX_HEIGHT = 220

/** Khung nhập câu hỏi: tự giãn chiều cao, Enter để gửi, Shift+Enter xuống dòng. */
export default function Composer({
  onSend,
  onStop,
  disabled,
  topic,
  onTopicChange,
  options,
  onOptionsChange,
  showModeOption = true,
  autoFocus = false,
}) {
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
      className="glass liquid-sheen relative rounded-[28px] px-4 pt-3.5 pb-3 transition-shadow duration-200 focus-within:shadow-[0_10px_40px_rgba(8,145,178,0.18),0_0_0_3px_rgba(34,211,238,0.18),inset_0_1px_0_rgba(255,255,255,0.9)]"
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
        <div className="flex items-center gap-1.5">
          <TopicSelect value={topic} onChange={onTopicChange} allLabel="Tất cả chủ đề" />
          <AdvancedOptions value={options} onChange={onOptionsChange} showMode={showModeOption} />
        </div>
        <div className="flex items-center gap-3">
          {value.length > MAX_QUESTION * 0.8 && (
            <span className="text-xs text-muted tabular-nums">
              {value.length}/{MAX_QUESTION}
            </span>
          )}
          {disabled && onStop ? (
            <button
              type="button"
              onClick={onStop}
              aria-label="Dừng"
              title="Dừng trả lời"
              className="grid h-10 w-10 place-items-center rounded-full bg-ink text-white shadow-[0_6px_18px_rgba(15,23,42,0.25)] transition hover:bg-ink-soft active:scale-95"
            >
              <StopIcon className="h-3.5 w-3.5" />
            </button>
          ) : (
          <button
            type="submit"
            disabled={disabled || !value.trim()}
            aria-label="Gửi"
            title="Gửi (Enter)"
            className="btn-cyan grid h-10 w-10 place-items-center"
          >
            <ArrowUpIcon className="h-[18px] w-[18px]" />
          </button>
          )}
        </div>
      </div>
    </form>
  )
}
