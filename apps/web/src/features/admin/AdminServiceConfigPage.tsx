import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Card } from '../../shared/ui/primitives'
import { AdminForm, Field, PageHeader, errText, useApiAction } from '../../admin/ui'
import { sysAdminApi, type CustomerServiceConfig } from './sysApi'

/**
 * 客服配置（Stage 2.2 商业入口基础）：
 * 单行配置（电话/微信/二维码 URL/服务时间/开关），公开端 /service 实时读取。
 * 不接第三方客服系统（配置化）。
 */
export default function AdminServiceConfigPage() {
  const [cfg, setCfg] = useState<CustomerServiceConfig | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const run = useApiAction()

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setCfg(await sysAdminApi.customerService())
    } catch (e) {
      setError(errText(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const save = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        sysAdminApi.updateCustomerService({
          phone: String(fd.get('phone') || '') || null,
          wechat: String(fd.get('wechat') || '') || null,
          qrCodeUrl: String(fd.get('qrCodeUrl') || '') || null,
          serviceTime: String(fd.get('serviceTime') || '') || null,
          enabled: fd.get('enabled') === 'on' ? 1 : 0,
        }),
      '客服配置已保存',
    )
    if (result) {
      void load()
    }
  }

  return (
    <div className="page">
      <PageHeader title="客服配置" sub="用户端「联系客服」页实时读取此配置（电话/微信/二维码/服务时间/开关）" />
      <Card>
        {error && <div className="inline-error">{error}</div>}
        {!loading && cfg && (
          <AdminForm onSubmit={save} submitLabel="保存">
            <Field label="客服电话（用户端点击可拨打）">
              <input className="input" name="phone" defaultValue={cfg.phone ?? ''} placeholder="400-800-1234" />
            </Field>
            <Field label="客服微信">
              <input className="input" name="wechat" defaultValue={cfg.wechat ?? ''} placeholder="yunsie_service" />
            </Field>
            <Field label="微信二维码图片 URL">
              <input
                className="input"
                name="qrCodeUrl"
                defaultValue={cfg.qrCodeUrl ?? ''}
                placeholder="https://example.com/qr.png"
              />
            </Field>
            <Field label="服务时间文案">
              <input className="input" name="serviceTime" defaultValue={cfg.serviceTime ?? ''} placeholder="工作日 9:00-18:00" />
            </Field>
            <Field label="启用客服入口">
              <input type="checkbox" name="enabled" defaultChecked={cfg.enabled === 1} />
            </Field>
          </AdminForm>
        )}
      </Card>
    </div>
  )
}
