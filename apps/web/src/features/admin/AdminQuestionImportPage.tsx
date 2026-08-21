import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, EmptyState } from '../../shared/ui/primitives'
import { DataTable, Field, PageHeader, errText } from '../../admin/ui'
import { certificateAdminApi, type CertItem } from './certificateAdminApi'
import { importQuestions, type ImportResult } from './questionAdminApi'

/** Excel 导入：上传状态 + 成功/失败反馈 + 行级错误展示（服务端最终校验，≤5000 行） */
export default function AdminQuestionImportPage() {
  const [certs, setCerts] = useState<CertItem[]>([])
  const [certId, setCertId] = useState<number | undefined>(undefined)
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    certificateAdminApi.certs(1, 100).then((p) => {
      setCerts(p.list)
      if (p.list.length > 0) setCertId(p.list[0].id)
    }).catch(() => {})
  }, [])

  const doImport = async () => {
    if (!file || certId === undefined) {
      setError('请选择证书与 Excel 文件')
      return
    }
    setUploading(true)
    setError('')
    setResult(null)
    try {
      const r = await importQuestions(file, certId)
      setResult(r)
    } catch (e) {
      setError(errText(e))
    } finally {
      setUploading(false)
    }
  }

  const templateUrl = `${(import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''}/api/v1/question/import-template`

  return (
    <div className="page">
      <PageHeader title="Excel 导入题目" sub="同步导入 · 上限 5000 行 · 行级错误 · 整体校验通过才入库" />
      <Card>
        <Field label="所属证书">
          <select className="input" value={certId ?? ''} onChange={(e) => setCertId(Number(e.target.value))}>
            {certs.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </Field>
        <div className="admin-toolbar">
          <label className={`upload-btn ${uploading ? 'upload-disabled' : ''}`}>
            选择 Excel 文件
            <input
              type="file"
              accept=".xlsx,.xls"
              disabled={uploading}
              onChange={(e) => {
                const f = e.target.files?.[0]
                setFile(f ?? null)
                e.target.value = ''
              }}
            />
          </label>
          {file && <span className="list-row-sub">{file.name}</span>}
          <button type="button" className="btn btn-primary btn-md" disabled={!file || uploading} onClick={() => void doImport()}>
            {uploading ? '上传中…' : '开始导入'}
          </button>
          <a className="link-btn" href={templateUrl}>
            下载导入模板
          </a>
        </div>
        {error && <div className="inline-error">{error}</div>}
        {result && (
          <div className={`import-result ${result.success ? 'import-ok' : 'import-fail'}`} role="status">
            <div className="import-summary">
              共 {result.total} 行 · 成功 {result.successCount} 行
              {result.success ? ' ✓ 全部入库' : ` · 失败 ${result.errors.length} 行（整体未入库）`}
            </div>
            {result.errors.length > 0 && (
              <DataTable
                rows={result.errors}
                rowKey={(r) => `${r.row}-${r.field}`}
                emptyText="无错误"
                columns={[
                  { key: 'row', title: '行号', render: (r) => <span className="num">{r.row}</span> },
                  { key: 'field', title: '字段' },
                  { key: 'message', title: '错误信息' },
                ]}
              />
            )}
          </div>
        )}
        {!result && !error && (
          <EmptyState icon="▤" title="尚未导入" hint="选择证书与 .xlsx 文件后开始导入" />
        )}
        <div className="admin-toolbar">
          <Link to="/admin/questions" className="link-btn">
            ← 返回题库管理
          </Link>
        </div>
      </Card>
    </div>
  )
}
