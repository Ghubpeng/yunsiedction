import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, Skeleton } from '../../shared/ui/primitives'
import { PageHeader } from '../../admin/ui'
import { sysAdminApi } from './sysApi'
import { certificateAdminApi } from './certificateAdminApi'
import { courseAdminApi } from './courseAdminApi'
import { questionAdminApi } from './questionAdminApi'
import { examAdminApi } from './examAdminApi'
import { contentOpsApi, type ContentOpsVO } from './contentOpsApi'

/** 管理端仪表盘：真实计数（pageSize=1 取 total）+ 内容运营看板（资产统计/运营提醒）+ 快捷入口 */
export default function AdminDashboardPage() {
  const [stats, setStats] = useState<{ label: string; value: number; to: string }[]>([])
  const [ops, setOps] = useState<ContentOpsVO | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    Promise.allSettled([
      sysAdminApi.users(1, 1),
      certificateAdminApi.certs(1, 1),
      courseAdminApi.page(1, 1),
      questionAdminApi.page({ pageNum: 1, pageSize: 1 }),
      examAdminApi.page(1, 1),
      contentOpsApi.dashboard(),
    ]).then(([users, certs, courses, questions, exams, opsResult]) => {
      if (cancelled) return
      const num = (r: PromiseSettledResult<{ total: number }>) => (r.status === 'fulfilled' ? r.value.total : 0)
      setStats([
        { label: '用户', value: num(users), to: '/admin/users' },
        { label: '证书', value: num(certs), to: '/admin/certificates' },
        { label: '课程', value: num(courses), to: '/admin/courses' },
        { label: '题目', value: num(questions), to: '/admin/questions' },
        { label: '考试', value: num(exams), to: '/admin/exams' },
        {
          label: '视频',
          value: opsResult.status === 'fulfilled' ? opsResult.value.assets.videos : 0,
          to: '/admin/courses',
        },
      ])
      setOps(opsResult.status === 'fulfilled' ? opsResult.value : null)
      setLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="page">
      <PageHeader title="仪表盘" sub="平台内容与用户概览" />
      {loading ? (
        <Card>
          <Skeleton lines={3} />
        </Card>
      ) : (
        <div className="stat-grid">
          {stats.map((s) => (
            <Link key={s.label} to={s.to} className="stat stat-link">
              <div className="stat-value num">{s.value}</div>
              <div className="stat-label">{s.label}</div>
            </Link>
          ))}
        </div>
      )}
      {ops && (
        <Card title="运营提醒">
          <ul className="plain-list">
            <li className="list-row">
              <div className="list-row-main">
                <div className="list-row-title">待审核题目：{ops.reminders.pendingReviewQuestions} 道</div>
                <div className="list-row-sub">发布唯一通道=审核通过，请及时处理</div>
              </div>
              <Link className="link-btn" to="/admin/questions">
                去审核
              </Link>
            </li>
            {ops.reminders.emptyChapters.map((e) => (
              <li key={e.chapterId} className="list-row">
                <div className="list-row-main">
                  <div className="list-row-title">空章节：{e.courseTitle ?? `课程#${e.courseId}`} / {e.chapterTitle}</div>
                  <div className="list-row-sub">该课程章节下还没有小节</div>
                </div>
                <Link className="link-btn" to={`/admin/courses/${e.courseId}`}>
                  去补充
                </Link>
              </li>
            ))}
            {ops.reminders.orphanChapters.map((o) => (
              <li key={o.nodeId} className="list-row">
                <div className="list-row-main">
                  <div className="list-row-title">无课程章节：{o.certificateName} / {o.nodeName}</div>
                  <div className="list-row-sub">该学习章节还没有课程归属</div>
                </div>
                <Link className="link-btn" to={`/admin/subjects?cert=${o.certificateId}`}>
                  去配置
                </Link>
              </li>
            ))}
            {ops.reminders.pendingReviewQuestions === 0 &&
              ops.reminders.emptyChapters.length === 0 &&
              ops.reminders.orphanChapters.length === 0 && (
                <li className="list-row-sub">暂无待处理提醒，内容运营状态良好</li>
              )}
          </ul>
        </Card>
      )}
      <Card title="内容冷启动清单">
        <ol className="plain-list">
          <li className="list-row">
            <div className="list-row-main">
              <div className="list-row-title">1. 证书与分类 → 知识体系（版本/科目/章节/知识点）</div>
              <div className="list-row-sub">平台内容的地基，先建证书与知识点</div>
            </div>
            <Link className="link-btn" to="/admin/certificates">
              去创建
            </Link>
          </li>
          <li className="list-row">
            <div className="list-row-main">
              <div className="list-row-title">2. 题库（创建或 Excel 导入 → 提审 → 审核通过）</div>
              <div className="list-row-sub">题目发布唯一通道 = 审核通过</div>
            </div>
            <Link className="link-btn" to="/admin/questions">
              去管理
            </Link>
          </li>
          <li className="list-row">
            <div className="list-row-main">
              <div className="list-row-title">3. 课程（章/节/视频 → 发布）与考试（组卷 → 发布）</div>
              <div className="list-row-sub">内容就绪后开放给学员</div>
            </div>
            <Link className="link-btn" to="/admin/courses">
              去管理
            </Link>
          </li>
        </ol>
      </Card>
    </div>
  )
}
