import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import SourceList, { sourceElementId } from './SourceList.jsx'

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

function scrollToSource(messageId, index) {
  const el = document.getElementById(sourceElementId(messageId, index))
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.remove('flash')
  void el.offsetWidth // restart animation
  el.classList.add('flash')
}

export default function AnswerMessage({ message }) {
  const { id, answer, found, sources, latencyMs } = message

  if (!found) {
    return (
      <div
        data-role="assistant"
        data-found="false"
        className="flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-100 px-4 py-3 text-slate-500"
      >
        <span>ℹ️</span>
        <div>
          <p>{answer}</p>
          <p className="mt-1 text-xs">Thử diễn đạt lại câu hỏi, bỏ bộ lọc chủ đề, hoặc nạp thêm tài liệu.</p>
        </div>
      </div>
    )
  }

  const validIndexes = new Set(sources.map((s) => s.index))
  return (
    <div data-role="assistant" data-found="true" className="rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <div className="markdown">
        <ReactMarkdown
          rehypePlugins={[rehypeHighlight]}
          components={{
            a: ({ href, children }) => {
              if (href?.startsWith('#cite-')) {
                const index = Number(href.slice('#cite-'.length))
                return (
                  <button
                    onClick={() => scrollToSource(id, index)}
                    className="mx-0.5 rounded bg-sky-50 px-1 align-super text-xs font-semibold text-sky-700 hover:bg-sky-100"
                    title="Xem nguồn"
                  >
                    {children}
                  </button>
                )
              }
              return (
                <a href={href} target="_blank" rel="noreferrer" className="text-sky-700 underline">
                  {children}
                </a>
              )
            },
          }}
        >
          {linkCitations(answer, validIndexes)}
        </ReactMarkdown>
      </div>
      <SourceList messageId={id} sources={sources} />
      {latencyMs != null && <div className="mt-2 text-right text-xs text-slate-400">{(latencyMs / 1000).toFixed(1)} giây</div>}
    </div>
  )
}
