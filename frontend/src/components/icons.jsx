/* Bộ icon nét mảnh tự vẽ (24×24, stroke = currentColor). */

function Icon({ d, className = 'h-[18px] w-[18px]', children }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {d && <path d={d} />}
      {children}
    </svg>
  )
}

export const PanelLeftIcon = (p) => (
  <Icon {...p}>
    <rect x="3" y="4" width="18" height="16" rx="2.5" />
    <path d="M9 4v16" />
  </Icon>
)
export const ExpandIcon = (p) => <Icon {...p} d="M14 4h6v6M10 20H4v-6M20 4l-6.5 6.5M4 20l6.5-6.5" />
export const ShrinkIcon = (p) => <Icon {...p} d="M4 14h6v6M20 10h-6V4M14 10l6.5-6.5M3.5 20.5 10 14" />
export const PlusIcon = (p) => <Icon {...p} d="M12 5v14M5 12h14" />
export const ArrowUpIcon = (p) => <Icon {...p} d="M12 19V5M5.5 11.5 12 5l6.5 6.5" />
export const TrashIcon = (p) => (
  <Icon {...p} d="M4 7h16M10 11v6M14 11v6M6 7l1 12.5A2 2 0 0 0 9 21.5h6a2 2 0 0 0 2-2L18 7M9 7V4.5h6V7" />
)
export const RefreshIcon = (p) => (
  <Icon {...p} d="M20 11A8 8 0 0 0 5.3 7M4 3.5V8h4.5M4 13a8 8 0 0 0 14.7 4M20 20.5V16h-4.5" />
)
export const InfoIcon = (p) => (
  <Icon {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 16v-4.5M12 8h.01" />
  </Icon>
)
export const ChevronDownIcon = (p) => <Icon {...p} d="m6 9 6 6 6-6" />
export const XIcon = (p) => <Icon {...p} d="M18 6 6 18M6 6l12 12" />
export const BookIcon = (p) => (
  <Icon {...p} d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 3H20v18H6.5A2.5 2.5 0 0 1 4 18.5v-13A2.5 2.5 0 0 1 6.5 3Z" />
)
export const UploadIcon = (p) => <Icon {...p} d="M12 15V4M7 9l5-5 5 5M5 20h14" />
export const NewChatIcon = (p) => <Icon {...p} d="M12 20h8M16.5 4.5a2.1 2.1 0 0 1 3 3L8 19l-4 1 1-4Z" />
export const FilesIcon = (p) => (
  <Icon {...p} d="M15 3H8a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V7Zm0 0v4h4M3 8v11a2 2 0 0 0 2 2h9" />
)

/** Nút chỉ có icon, luôn có nhãn cho trình đọc màn hình và tooltip. */
export function IconButton({ label, onClick, children, className = '', small = false, disabled = false }) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      onClick={onClick}
      disabled={disabled}
      className={`grid shrink-0 place-items-center rounded-lg text-ink-soft transition hover:bg-hover hover:text-ink disabled:pointer-events-none disabled:opacity-40 ${
        small ? 'h-7 w-7' : 'h-8 w-8'
      } ${className}`}
    >
      {children}
    </button>
  )
}
