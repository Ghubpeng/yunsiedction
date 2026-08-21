import { useEffect, useState } from 'react'
import { serviceApi, type CustomerServiceConfig } from './api'

const FAQS = [
  { q: '如何开始学习？', a: '在首页选择你的考试目标，系统会自动为你安排课程、题库与模拟考试；按「课程 → 章节练习 → 模拟考试」的顺序学习即可。' },
  { q: '学习进度会自动保存吗？', a: '会。视频学习进度每 5 秒自动保存，离开页面也会保存；练习与考试成绩实时记录在学习档案中。' },
  { q: '如何查看自己的薄弱点？', a: '进入「我的 → 学习档案」，系统根据你的练习与考试数据自动生成薄弱点与掌握度。' },
  { q: '模拟考试能考几次？', a: '不限次数。每次考试独立计时、独立判分，历史成绩都会保留在考试中心。' },
  { q: '课程如何购买？', a: '购买功能尚未开通（支付能力接入中）。当前公开课程均可直接学习。' },
]

/**
 * 联系客服 /service（Stage 2.2 商业入口基础）：
 * 电话 / 微信 / 二维码 / 服务时间 / FAQ —— 全部配置化（sys_customer_service），
 * 不接第三方客服系统；配置未启用时诚实展示「暂未开通」。
 */
export default function ServicePage() {
  const [cfg, setCfg] = useState<CustomerServiceConfig | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    serviceApi
      .config()
      .then((c) => {
        if (!cancelled) setCfg(c)
      })
      .catch(() => {
        /* 配置读取失败按未开通展示 */
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const enabled = cfg != null && cfg.enabled === 1

  return (
    <div className="ed-container ed-page">
      <header className="ed-page-head">
        <h1 className="ed-title">联系客服</h1>
        <p className="ed-lead">学习中遇到任何问题，都可以联系我们。</p>
      </header>

      {loading ? (
        <div className="ed-skeleton" style={{ height: 160, borderRadius: 'var(--ed-radius-xl)' }} aria-hidden="true" />
      ) : !enabled ? (
        <div className="ed-card ed-card-pad" style={{ textAlign: 'center' }}>
          <div className="ed-empty-icon" aria-hidden="true">
            ✉
          </div>
          <p className="ed-empty-title">客服通道暂未开通</p>
          <p className="ed-empty-hint">请稍后再来，或先查看下方常见问题。</p>
        </div>
      ) : (
        <div className="ed-grid ed-grid-2" style={{ marginBottom: 56 }}>
          {(cfg.phone || cfg.wechat || cfg.qrCodeUrl) && (
            <div className="ed-card ed-card-pad">
              <h2 className="ed-h3" style={{ marginBottom: 16 }}>
                联系方式
              </h2>
              <ul className="ed-list">
                {cfg.phone && (
                  <li className="ed-row" style={{ padding: '12px 0' }}>
                    <span className="ed-row-title">客服电话</span>
                    <a href={`tel:${cfg.phone}`} className="ed-arrow-link">
                      {cfg.phone}
                    </a>
                  </li>
                )}
                {cfg.wechat && (
                  <li className="ed-row" style={{ padding: '12px 0' }}>
                    <span className="ed-row-title">客服微信</span>
                    <span className="num">{cfg.wechat}</span>
                  </li>
                )}
                {cfg.qrCodeUrl && (
                  <li className="ed-row" style={{ padding: '12px 0' }}>
                    <span className="ed-row-title">微信二维码</span>
                    <img
                      src={cfg.qrCodeUrl}
                      alt="客服微信二维码"
                      style={{ width: 132, height: 132, borderRadius: 12, objectFit: 'contain' }}
                    />
                  </li>
                )}
              </ul>
            </div>
          )}
          <div className="ed-card ed-card-pad">
            <h2 className="ed-h3" style={{ marginBottom: 16 }}>
              服务时间
            </h2>
            <p className="ed-lead" style={{ fontSize: '1rem' }}>
              {cfg.serviceTime || '以官方公告为准'}
            </p>
            <p className="ed-caption" style={{ marginTop: 8 }}>
              人工服务仅在工作时间内响应，其余时间可留言。
            </p>
          </div>
        </div>
      )}

      <section aria-label="常见问题">
        <h2 className="ed-h2" style={{ fontSize: '1.5rem', marginBottom: 20 }}>
          常见问题
        </h2>
        <div className="ed-list">
          {FAQS.map((f) => (
            <details key={f.q} className="ed-row" style={{ padding: '16px 0', display: 'block' }}>
              <summary style={{ cursor: 'pointer', fontWeight: 500, fontSize: '1.0625rem' }}>{f.q}</summary>
              <p className="ed-card-sub" style={{ marginTop: 8 }}>
                {f.a}
              </p>
            </details>
          ))}
        </div>
      </section>
    </div>
  )
}
