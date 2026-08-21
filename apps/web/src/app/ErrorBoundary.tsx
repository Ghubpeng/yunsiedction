import { Component, type ErrorInfo, type ReactNode } from 'react'

interface State {
  error: Error | null
}

/** 页面级错误边界（frontend-development §4.8：崩溃不得白屏） */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        <div className="page">
          <div className="empty-state">
            <div className="empty-icon" aria-hidden="true">
              ⚠
            </div>
            <p className="empty-title">页面出错了</p>
            <p className="empty-hint">请刷新页面重试，或稍后再来。</p>
            <button type="button" className="btn btn-primary btn-md" onClick={() => window.location.reload()}>
              刷新页面
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}
