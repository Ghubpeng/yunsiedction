import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../shared/auth/AuthContext'
import { friendlyMessage } from '../../shared/api/types'

/**
 * 登录页（Stage 2.1 编辑式分屏）：
 * 左侧品牌陈述（公开），右侧登录表单；Demo 入口仅本地开发/演示构建出现
 * （import.meta.env.DEV 或 VITE_DEMO_ENABLED=true），生产构建不渲染。
 * 登录成功后回到来源页（from state），无来源则回首页。
 */
export default function LoginPage() {
  const { login, demoLogin } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const demoEnabled = import.meta.env.DEV || import.meta.env.VITE_DEMO_ENABLED === 'true'

  const finish = (target: string) => {
    navigate(target, { replace: true })
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!account || !password) {
      setError('请输入账号与密码')
      return
    }
    setError('')
    setLoading(true)
    try {
      await login(account, password)
      finish(from)
    } catch (err) {
      setError(friendlyMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const onDemo = async (role: 'learner' | 'teacher' | 'admin' = 'learner') => {
    setError('')
    setLoading(true)
    try {
      await demoLogin(role)
      // 管理员演示：整页直达 /admin（避免与 LoginRoute 登录态重定向竞态；
      // 全量加载后权限上下文正确初始化）。学员/教师走来源页（登录态重定向同目标）。
      if (role === 'admin') {
        window.location.assign('/admin')
      } else {
        finish(from)
      }
    } catch (err) {
      setError(friendlyMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="ed-shell">
      <div className="ed-login">
        <aside className="ed-login-statement">
          <Link to="/" className="ed-nav-brand" style={{ marginBottom: 48, display: 'inline-block' }}>
            云思学习
          </Link>
          <span className="ed-eyebrow">职业资格考试培训</span>
          <h1 className="ed-display" style={{ fontSize: 'var(--ed-h1)', maxWidth: '10em' }}>
            开始你的备考旅程
          </h1>
          <p className="ed-lead" style={{ marginTop: 20 }}>
            课程、题库、模拟考试与学习档案，为你保留每一点进步。
          </p>
          <p className="ed-caption" style={{ marginTop: 40 }}>
            还没有账号？联系管理员开通，或先
            <Link to="/courses" className="ed-text-btn">
              浏览公开课程
            </Link>
            。学习遇到问题？
            <Link to="/service" className="ed-text-btn">
              联系客服
            </Link>
          </p>
        </aside>

        <div className="ed-login-panel">
          <form className="ed-login-card" onSubmit={onSubmit} aria-label="登录">
            <h2 className="ed-h2" style={{ marginBottom: 8 }}>
              登录
            </h2>
            <p className="ed-caption" style={{ marginBottom: 32 }}>
              使用你的账号继续学习
            </p>
            <label className="ed-field">
              <span className="ed-label">账号</span>
              <input
                id="account"
                className="ed-input"
                value={account}
                onChange={(e) => setAccount(e.target.value)}
                autoComplete="username"
                placeholder="请输入账号"
              />
            </label>
            <label className="ed-field">
              <span className="ed-label">密码</span>
              <input
                id="password"
                className="ed-input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                placeholder="请输入密码"
              />
            </label>
            {error && (
              <div className="ed-error inline-error" role="alert" style={{ marginBottom: 20 }}>
                <span>{error}</span>
              </div>
            )}
            <button type="submit" className="ed-btn ed-btn-lg" style={{ width: '100%' }} disabled={loading}>
              {loading ? '正在登录…' : '登录'}
            </button>
            {demoEnabled && (
              <>
                <hr className="ed-divider" style={{ margin: '28px 0' }} />
                <p className="ed-caption" style={{ marginBottom: 12, textAlign: 'center' }}>
                  演示环境 · 一键体验（仅开发环境出现，生产构建无此入口）
                </p>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <button
                    type="button"
                    className="ed-btn ed-btn-lg ed-btn-ghost"
                    style={{ width: '100%' }}
                    onClick={() => onDemo('learner')}
                    disabled={loading}
                  >
                    进入 Demo · 学员体验
                  </button>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                    <button
                      type="button"
                      className="ed-btn ed-btn-sm ed-btn-ghost"
                      onClick={() => onDemo('teacher')}
                      disabled={loading}
                    >
                      教师体验
                    </button>
                    <button
                      type="button"
                      className="ed-btn ed-btn-sm ed-btn-ghost"
                      onClick={() => onDemo('admin')}
                      disabled={loading}
                    >
                      管理员体验
                    </button>
                  </div>
                </div>
              </>
            )}
          </form>
        </div>
      </div>
    </div>
  )
}
