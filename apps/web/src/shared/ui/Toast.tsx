import {
  createContext,
  useCallback,
  useContext,
  useRef,
  useState,
  type ReactNode,
} from 'react'

/** 全局 Toast（轻量内联实现，不引入 UI 库；动效可被 prefers-reduced-motion 降级） */
type ToastKind = 'info' | 'success' | 'error'

interface ToastItem {
  id: number
  kind: ToastKind
  text: string
}

interface ToastContextValue {
  toast: (text: string, kind?: ToastKind) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

let nextId = 1

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const timers = useRef<Map<number, number>>(new Map())

  const toast = useCallback((text: string, kind: ToastKind = 'info') => {
    const id = nextId++
    setItems((prev) => [...prev, { id, kind, text }])
    const timer = window.setTimeout(() => {
      setItems((prev) => prev.filter((t) => t.id !== id))
      timers.current.delete(id)
    }, 3200)
    timers.current.set(id, timer)
  }, [])

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="toast-host" role="status" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`toast toast-${t.kind}`}>
            {t.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) {
    throw new Error('useToast must be used within ToastProvider')
  }
  return ctx
}
