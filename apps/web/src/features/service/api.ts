import { request } from '../../shared/api/client'

/** 客服联系方式配置（公开读取；配置化，不接第三方客服系统） */
export interface CustomerServiceConfig {
  phone: string | null
  wechat: string | null
  qrCodeUrl: string | null
  serviceTime: string | null
  enabled: number
}

export const serviceApi = {
  config: () => request<CustomerServiceConfig>('/api/v1/service/customer-service'),
}
