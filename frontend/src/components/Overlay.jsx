import { createPortal } from 'react-dom'

/**
 * Render modal ra thẳng document.body. Bắt buộc với modal nằm trong panel kính: phần tử có backdrop-filter
 * trở thành khung chứa của mọi con `position: fixed`, nên modal bị kẹt trong panel thay vì phủ cả màn hình.
 */
export function Portal({ children }) {
  return createPortal(children, document.body)
}

/** Lớp phủ "đang tải" khi chờ tải code của trình xem PDF (fallback của Suspense). */
export function LoadingOverlay({ children }) {
  return (
    <Portal>
      <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/30 text-sm text-white backdrop-blur-sm">
        {children}
      </div>
    </Portal>
  )
}
