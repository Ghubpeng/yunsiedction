import { useState } from 'react'
import { AI_READY, SCENARIO_ACTIONS, type AiContext, type AiScenario, type NextStepRules } from './protocol'

/**
 * AI 学习助手面板（P1 体验层 + Stage 2.3B 规则推荐）：
 * - AI_READY=false 时诚实降级：展示已捕获的真实上下文 + 快捷操作入口，
 *   并明确标注「AI 服务启用后提供智能讲解」，绝不冒充 AI 已生成内容。
 * - nextStep 属性传入时（首页），展示基于真实学习数据的规则推荐（薄弱知识点/推荐课程/推荐练习/学习下一步），
 *   明确标注「非 AI 生成」。
 * - AI_READY=true 后：本组件按场景+动作调用后端 AI 会话接口（协议已定义，接入点在此）。
 */
export function AiAssistant({
  context,
  triggerLabel = '学习助手',
  nextStep,
}: {
  context: AiContext
  triggerLabel?: string
  nextStep?: NextStepRules | null
}) {
  const [open, setOpen] = useState(false)

  return (
    <div className="ed-card" style={{ borderRadius: 'var(--ed-radius-lg)' }}>
      {!open ? (
        <button
          type="button"
          className="ed-btn ed-btn-sm ed-btn-ghost"
          style={{ margin: 12 }}
          onClick={() => setOpen(true)}
        >
          ✦ {triggerLabel}
        </button>
      ) : (
        <div className="ed-card-pad" style={{ padding: 20 }}>
          <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 12, marginBottom: 12 }}>
            <h3 className="ed-h3" style={{ fontSize: '1.0625rem' }}>
              ✦ 学习助手
            </h3>
            <button type="button" className="ed-text-btn" onClick={() => setOpen(false)}>
              收起
            </button>
          </div>

          {nextStep && (
            <div className="ed-list" style={{ marginBottom: 12 }}>
              <div className="ed-row-title" style={{ margin: '6px 0' }}>
                今日学习建议（规则推荐）
              </div>
              {nextStep.weakKnowledge.length > 0 && (
                <div className="ed-row" style={{ padding: '8px 0' }}>
                  <div className="ed-row-main">
                    <div className="ed-row-title">薄弱知识点</div>
                    <div className="ed-row-sub">
                      {nextStep.weakKnowledge.map((w) => `${w.name ?? `节点#${w.nodeId}`}（${w.reason}）`).join('、')}
                    </div>
                  </div>
                </div>
              )}
              {nextStep.recommendedCourses.length > 0 && (
                <div className="ed-row" style={{ padding: '8px 0' }}>
                  <div className="ed-row-main">
                    <div className="ed-row-title">推荐课程</div>
                    <div className="ed-row-sub">{nextStep.recommendedCourses.map((c) => `《${c.title}》`).join('、')}</div>
                  </div>
                </div>
              )}
              {nextStep.recommendedPractice.length > 0 && (
                <div className="ed-row" style={{ padding: '8px 0' }}>
                  <div className="ed-row-main">
                    <div className="ed-row-title">推荐练习</div>
                    <div className="ed-row-sub">{nextStep.recommendedPractice.length} 道针对性练习</div>
                  </div>
                </div>
              )}
              <div className="ed-row" style={{ padding: '8px 0' }}>
                <div className="ed-row-main">
                  <div className="ed-row-title">学习下一步</div>
                  <div className="ed-row-sub">{nextStep.nextStep}</div>
                </div>
              </div>
              <p className="ed-caption" style={{ margin: '6px 0 0', fontSize: '0.75rem' }}>
                基于真实学习数据的规则推荐 · 非 AI 生成
              </p>
            </div>
          )}

          <div className="ed-list" style={{ marginBottom: 12 }}>
            {SCENARIO_ACTIONS[context.scenario].map((a) => (
              <div key={a.label} className="ed-row" style={{ padding: '10px 0' }}>
                <div className="ed-row-main">
                  <div className="ed-row-title">{a.label}</div>
                  <div className="ed-row-sub">{a.description}</div>
                </div>
              </div>
            ))}
          </div>

          {/* 已捕获上下文（证明场景上下文已携带，未来随请求下发 ai-service） */}
          <details style={{ fontSize: '0.8125rem', color: 'var(--ed-ink-3)', marginBottom: 12 }}>
            <summary style={{ cursor: 'pointer' }}>已携带上下文</summary>
            <pre style={{ whiteSpace: 'pre-wrap', margin: '8px 0 0', fontSize: '0.75rem' }}>
              {JSON.stringify(publicContext(context), null, 2)}
            </pre>
          </details>

          {AI_READY ? (
            <button type="button" className="ed-btn ed-btn-sm" style={{ width: '100%' }}>
              开始对话
            </button>
          ) : (
            <p className="ed-caption" style={{ background: 'var(--ed-bg-subtle)', borderRadius: 12, padding: '10px 14px', margin: 0 }}>
              AI 助手将在 AI 服务启用后提供智能讲解。当前展示基于你的真实学习数据，不含模型生成内容。
            </p>
          )}
        </div>
      )}
    </div>
  )
}

/** 供 UI 展示的公开上下文（剔除敏感字段，仅展示） */
function publicContext(ctx: AiContext): Partial<AiContext> {
  const { userId: _uid, ...rest } = ctx
  void _uid
  return rest
}

export type { AiScenario }
