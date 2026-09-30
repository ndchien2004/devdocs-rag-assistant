import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import SourceList, { sourceElementId } from './SourceList.jsx'
import { InfoIcon } from './icons.jsx'

const CITATION = /\[(\d+(?:\s*,\s*\d+)*)\]/g
// Không biến [n] thành link khi nằm trong code (```...``` hoặc `...`), ví dụ arr[0].
const CODE = /(```[\s\S]*?```|`[^`\n]*`)/

/** "[1][3]" → "[\[1\]](#cite-1)[\[3\]](#cite-3)" — chỉ với số có trong danh sách nguồn. */
export function linkCitations(text, validIndexes) {
  return text
    .split(CODE)
    .map((part, i) => {
      if (i % 2 === 1) return part // phần code
      return part.replace(CITATION, (match, group) => {
        const numbers = group.split(',').map((n) => Number(n.trim()))
        if (!numbers.every((n) => validIndexes.has(n))) return match
        return numbers.map((n) => `[\\[${n}\\]](#cite-${n})`).join('')
      })
    })
    .join('')
}

export default function AnswerMessage({ message }) {
  const { id, answer, found, sources, latencyMs } = message
  const [openSource, setOpenSource] = useState(null)

  if (!found) {
    return (
      <div data-role="assistant" data-found="false" className="flex gap-3 text-ink-soft">
        <InfoIcon className="mt-1 h-[18px] w-[18px] shrink-0 text-muted" />
        <div>
          <p className="font-serif text-[16.5px] leading-7">{answer}</p>
          <p className="mt-1 text-sm text-muted">Thử diễn đạt lại câu hỏi, bỏ lọc chủ đề, hoặc nạp thêm tài liệu.</p>
        </div>
      </div>
    )
  }

  function showSource(index) {
    setOpenSource(index)
    const el = document.getElementById(sourceElementId(id, index))
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    el.classList.remove('flash')
    void el.offsetWidth // chạy lại animation
    el.classList.add('flash')
  }

  const validIndexes = new Set(sources.map((s) => s.index))
  return (
    <div data-role="assistant" data-found="true">
      <div className="markdown font-serif text-[16.5px] leading-[1.7] text-ink">
        <ReactMarkdown
          rehypePlugins={[rehypeHighlight]}
          components={{
            a: ({ href, children }) => {
              if (href?.startsWith('#cite-')) {
                const index = Number(href.slice('#cite-'.length))
                return (
                  <button
                    type="button"
                    onClick={() => showSource(index)}
                    title="Xem nguồn"
                    className="mx-[1px] inline-grid h-[18px] min-w-[18px] place-items-center rounded-md bg-accent-soft px-1 align-[2px] font-sans text-[11px] font-semibold text-accent-strong transition hover:bg-accent hover:text-white"
                  >
                    {String(children).replace(/[[\]]/g, '')}
                  </button>
                )
              }
              return (
                <a href={href} target="_blank" rel="noreferrer" className="text-accent underline underline-offset-2">
                  {children}
                </a>
              )
            },
          }}
        >
          {linkCitations(answer, validIndexes)}
        </ReactMarkdown>
      </div>
      <SourceList messageId={id} sources={sources} openIndex={openSource} onToggle={setOpenSource} />
      {latencyMs != null && <p className="mt-2.5 text-xs text-muted">{(latencyMs / 1000).toFixed(1)} giây</p>}
    </div>
  )
}
