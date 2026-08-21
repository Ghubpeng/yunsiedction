import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge, Card } from '../../shared/ui/primitives'
import { DataTable, PageHeader, errText } from '../../admin/ui'
import { courseAdminApi, type CourseItem } from './courseAdminApi'
import { studentAdminApi, type StudentListItem, type StudentProfile } from './studentAdminApi'

/** 学员档案：教师经既有 TeacherAccessGuard 只能看自己课程范围学员；管理员 hasAllData 覆盖 */
export default function AdminStudentsPage() {
  const [courses, setCourses] = useState<CourseItem[]>([])
  const [courseId, setCourseId] = useState<number | undefined>(undefined)
  const [students, setStudents] = useState<StudentListItem[]>([])
  const [selected, setSelected] = useState<StudentProfile | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    courseAdminApi
      .page(1, 100)
      .then((p) => {
        setCourses(p.list)
        if (p.list.length > 0) setCourseId(p.list[0].id)
      })
      .catch((e) => setError(errText(e)))
  }, [])

  useEffect(() => {
    if (courseId === undefined) return
    setLoading(true)
    setError('')
    studentAdminApi
      .listByCourse(courseId)
      .then(setStudents)
      .catch((e) => setError(errText(e)))
      .finally(() => setLoading(false))
  }, [courseId])

  const openDetail = (userId: number) => {
    setError('')
    studentAdminApi
      .detail(userId)
      .then(setSelected)
      .catch((e) => setError(errText(e)))
  }

  return (
    <div className="page">
      <PageHeader title="学员档案" sub="数据范围 = 教师名下课程学员（服务端 DataScope 裁决）" />
      <div className="admin-toolbar">
        <select className="input" aria-label="选择课程" value={courseId ?? ''} onChange={(e) => setCourseId(Number(e.target.value))}>
          {courses.map((c) => (
            <option key={c.id} value={c.id}>
              {c.title}
            </option>
          ))}
        </select>
      </div>
      {error && <div className="inline-error">{error}</div>}

      <Card title="课程学员">
        <DataTable<StudentListItem>
          loading={loading}
          rows={students}
          rowKey={(r) => r.userId}
          emptyText="该课程暂无学员学习记录"
          columns={[
            { key: 'userId', title: 'ID', render: (r) => <span className="num">{r.userId}</span> },
            { key: 'nickname', title: '昵称' },
            { key: 'practiceCount', title: '练习', render: (r) => <span className="num">{r.summary.practiceCount}</span> },
            { key: 'examCount', title: '考试', render: (r) => <span className="num">{r.summary.examCount}</span> },
            { key: 'streak', title: '连续学习', render: (r) => <span className="num">{r.summary.streakDays} 天</span> },
            {
              key: 'actions',
              title: '操作',
              render: (r) => (
                <button type="button" className="link-btn" onClick={() => void openDetail(r.userId)}>
                  查看档案
                </button>
              ),
            },
          ]}
        />
      </Card>

      {selected && (
        <Card title={`档案详情 · ${selected.nickname ?? selected.userId}`} extra={<Link to="/admin/students" className="link-btn">收起</Link>}>
          <div className="stat-grid">
            <div className="stat">
              <div className="stat-value num">{Math.round(selected.summary.totalStudySeconds / 60)}</div>
              <div className="stat-label">学习分钟</div>
            </div>
            <div className="stat">
              <div className="stat-value num">{selected.summary.practiceCount}</div>
              <div className="stat-label">练习</div>
            </div>
            <div className="stat">
              <div className="stat-value num">{selected.summary.examCount}</div>
              <div className="stat-label">考试</div>
            </div>
            <div className="stat">
              <div className="stat-value num">{selected.summary.streakDays}</div>
              <div className="stat-label">连续学习</div>
            </div>
          </div>
          <h3 className="section-title">掌握度 / 薄弱点</h3>
          <DataTable
            rows={selected.mastery}
            rowKey={(r) => `${r.nodeId}-${r.nodeType}`}
            emptyText="暂无掌握度数据"
            columns={[
              { key: 'nodeName', title: '知识点' },
              { key: 'masteryValue', title: '掌握度', render: (r) => <Badge tone={r.masteryValue < 30 ? 'error' : r.masteryValue < 70 ? 'warning' : 'success'}>{r.masteryValue}</Badge> },
              { key: 'correctCount', title: '对', render: (r) => <span className="num">{r.correctCount}</span> },
              { key: 'wrongCount', title: '错', render: (r) => <span className="num">{r.wrongCount}</span> },
            ]}
          />
          {selected.weakness.length > 0 && (
            <DataTable
              rows={selected.weakness}
              rowKey={(r) => `w-${r.nodeId}`}
              emptyText="无薄弱点"
              columns={[
                { key: 'nodeName', title: '薄弱点' },
                { key: 'mastery', title: '掌握度', render: (r) => <span className="num">{r.mastery}</span> },
                { key: 'wrongCount', title: '错题', render: (r) => <span className="num">{r.wrongCount}</span> },
              ]}
            />
          )}
        </Card>
      )}
    </div>
  )
}
