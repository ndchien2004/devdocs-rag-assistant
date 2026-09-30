import { TOPICS } from '../constants.js'
import { ChevronDownIcon } from './icons.jsx'

/** Select dạng "pill" gọn, dùng trong khung nhập câu hỏi và form upload. */
export default function TopicSelect({ value, onChange, allLabel, className = '' }) {
  return (
    <div className={`relative ${className}`}>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-label="Chủ đề"
        className="w-full cursor-pointer appearance-none rounded-lg border border-line bg-white py-1.5 pr-7 pl-2.5 text-xs font-medium text-ink-soft transition hover:border-line-strong focus:border-accent focus:outline-none"
      >
        {allLabel && <option value="">{allLabel}</option>}
        {TOPICS.map((t) => (
          <option key={t.value} value={t.value}>
            {t.label}
          </option>
        ))}
      </select>
      <ChevronDownIcon className="pointer-events-none absolute top-1/2 right-2 h-3.5 w-3.5 -translate-y-1/2 text-muted" />
    </div>
  )
}
