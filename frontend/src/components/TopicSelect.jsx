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
        className="chip w-full cursor-pointer appearance-none py-1.5 pr-8 pl-3.5 text-xs font-medium text-ink-soft focus:outline-none"
      >
        {allLabel && <option value="">{allLabel}</option>}
        {TOPICS.map((t) => (
          <option key={t.value} value={t.value}>
            {t.label}
          </option>
        ))}
      </select>
      <ChevronDownIcon className="pointer-events-none absolute top-1/2 right-2.5 h-3.5 w-3.5 -translate-y-1/2 text-muted" />
    </div>
  )
}
