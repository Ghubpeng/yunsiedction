import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Badge, Card, Skeleton } from '../../shared/ui/primitives'
import { DataTable, PageHeader, Pagination, StatusBadge, errText } from '../../admin/ui'
import { examAdminApi, type ExamItem, type PaperQuestion, type PaperSummary, type ExamResultItem } from './examAdminApi'

const EXAM_STATUS: Record<number, string> = { 1: '草稿', 2: '已发布', 3: '已下架' }
const Q_TYPE: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断' }

/** 考试详情：基本信息 / 试卷（快照不可变）/ 成绩（只读） */
export default function AdminExamDetailPage() {
  const { id } = useParams()
  const examId = Number(id)
  const [tab, setTab] = useState<'info' | 'paper' | 'results'>('info')
  const [exam, setExam] = useState<ExamItem | null>(null)
  const [paper, setPaper] = useState<PaperSummary | null>(null)
  const [questions, setQuestions] = useState<PaperQuestion[]>([])
  const [results, setResults] = useState<ExamResultItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    examAdminApi
      .detail(examId)
      .then((e) => {
        if (!cancelled) setExam(e)
      })
      .catch((e) => {
        if (!cancelled) setError(errText(e))
      })
    return () => {
      cancelled = true
    }
  }, [examId])

  useEffect(() => {
    if (tab === 'paper' || tab === 'results') {
      setError('')
      Promise.allSettled([
        examAdminApi.paperSummary(examId),
        examAdminApi.paperQuestions(examId),
        examAdminApi.results(examId, pageNum, 20),
      ])
        .then(([p, q, r]) => {
          if (p.status === 'fulfilled') setPaper(p.value)
          if (q.status === 'fulfilled') setQuestions(q.value)
          if (r.status === 'fulfilled') {
            setResults(r.value.list)
            setTotal(r.value.total)
          }
        })
        .catch(() => setError('数据加载失败'))
    }
  }, [tab, examId, pageNum])

  if (!exam && !error) {
    return (
      <div className="page">
        <Card>
          <Skeleton lines={5} />
        </Card>
      </div>
    )
  }
  if (error || !exam) {
    return (
      <div className="page">
        <div className="inline-error">{error || '考试不存在'}</div>
        <Link to="/admin/exams" className="link-btn">
          ← 返回考试管理
        </Link>
      </div>
    )
  }

  return (
    <div className="page">
      <Link to="/admin/exams" className="link-btn">
        ← 返回考试管理
      </Link>
      <PageHeader title={exam.name} sub={`ID ${exam.id} · 时长 ${exam.durationMinutes} 分钟 · 及格 ${exam.passScore} 分`} />
      <div className="seg-row" role="tablist" aria-label="考试详情页签">
        {(
          [
            ['info', '基本信息'],
            ['paper', '试卷'],
            ['results', '成绩'],
          ] as const
        ).map(([key, label]) => (
          <button key={key} type="button" role="tab" aria-selected={tab === key} className={`seg-item ${tab === key ? 'seg-active' : ''}`} onClick={() => setTab(key)}>
            {label}
          </button>
        ))}
      </div>

      {tab === 'info' && (
        <Card title="基本信息">
          <div className="stat-grid">
            <div className="stat">
              <div className="stat-label">状态</div>
              <div className="stat-value">
                <StatusBadge labels={EXAM_STATUS} value={exam.status} />
              </div>
            </div>
            <div className="stat">
              <div className="stat-label">题目总数</div>
              <div className="stat-value num">{exam.rule.questionCount}</div>
            </div>
            <div className="stat">
              <div className="stat-label">题型</div>
              <div className="stat-value">{exam.rule.questionTypes.map((t) => Q_TYPE[t] ?? t).join(' / ')}</div>
            </div>
            <div className="stat">
              <div className="stat-label">开放时间</div>
              <div className="stat-value">{exam.validFrom ?? '不限'}</div>
            </div>
          </div>
        </Card>
      )}

      {tab === 'paper' && (
        <Card title={paper ? `试卷 #${paper.paperId}（快照不可变）· ${paper.questionCount} 题 · 总分 ${paper.totalScore}` : '试卷'}>
          <DataTable<PaperQuestion>
            rows={questions}
            rowKey={(r) => r.paperQuestionId}
            emptyText="尚未组卷（试卷为空）"
            columns={[
              { key: 'sort', title: '#', width: '50px', render: (r) => <span className="num">{r.sort}</span> },
              { key: 'questionType', title: '题型', render: (r) => <Badge>{Q_TYPE[r.questionType] ?? r.questionType}</Badge> },
              { key: 'stem', title: '题干', render: (r) => <span className="ellipsis-cell">{r.stem}</span> },
              { key: 'standardAnswer', title: '答案' },
              { key: 'score', title: '分值', render: (r) => <span className="num">{r.score}</span> },
            ]}
          />
        </Card>
      )}

      {tab === 'results' && (
        <Card title="成绩（只读）">
          <DataTable<ExamResultItem>
            rows={results}
            rowKey={(r) => r.attemptId}
            emptyText="暂无交卷记录"
            columns={[
              { key: 'attemptId', title: 'attempt', render: (r) => <span className="num">{r.attemptId}</span> },
              { key: 'score', title: '得分', render: (r) => <span className="num">{r.score}</span> },
              { key: 'correctCount', title: '答对', render: (r) => <span className="num">{r.correctCount}/{r.questionCount}</span> },
              { key: 'passScore', title: '及格分', render: (r) => <span className="num">{r.passScore}</span> },
              { key: 'passed', title: '结果', render: (r) => <Badge tone={r.passed ? 'success' : 'error'}>{r.passed ? '通过' : '未通过'}</Badge> },
              { key: 'submittedAt', title: '交卷时间' },
            ]}
          />
          <Pagination pageNum={pageNum} pageSize={20} total={total} onChange={(p) => setPageNum(p)} />
        </Card>
      )}
    </div>
  )
}
