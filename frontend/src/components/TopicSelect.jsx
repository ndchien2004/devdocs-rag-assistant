import { TOPICS } from '../constants.js'

export default function TopicSelect({ value, onChange, allLabel, className = '' }) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={`rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm focus:border-sky-500 focus:outline-none ${className}`}
    >
      {allLabel && <option value="">{allLabel}</option>}
      {TOPICS.map((t) => (
        <option key={t.value} value={t.value}>
          {t.label}
        </option>
      ))}
    </select>
  )
}
