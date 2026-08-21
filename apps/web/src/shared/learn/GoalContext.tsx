import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useAuth } from '../auth/AuthContext'
import { goalApi, type GoalView } from '../../features/learn/api'

interface GoalContextValue {
  /** 当前考试目标（未选择 = null；Stage 2.2 证书级目标） */
  goal: GoalView | null
  loading: boolean
  error: string
  refresh: () => Promise<void>
  /** 只选证书即可建立目标（后端自动关联当前版本与全部科目） */
  select: (certificateId: number, subjectId?: number) => Promise<GoalView>
}

const GoalContext = createContext<GoalContextValue | null>(null)

/**
 * 用户考试目标上下文（Stage 2.1 学习产品化）：
 * 当前目标成为首页/课程/练习/考试的主要上下文；切换=状态流转（后端保留历史）。
 */
export function GoalProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const [goal, setGoal] = useState<GoalView | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const refresh = useCallback(async () => {
    try {
      setError('')
      const g = await goalApi.current()
      setGoal(g)
    } catch {
      setError('目标加载失败，请刷新重试')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (isAuthenticated) {
      setLoading(true)
      void refresh()
    } else {
      setGoal(null)
      setLoading(false)
    }
  }, [isAuthenticated, refresh])

  const select = useCallback(
    async (certificateId: number, subjectId?: number) => {
      const g = await goalApi.select(certificateId, subjectId)
      setGoal(g)
      return g
    },
    [],
  )

  const value = useMemo(
    () => ({ goal, loading, error, refresh, select }),
    [goal, loading, error, refresh, select],
  )
  return <GoalContext.Provider value={value}>{children}</GoalContext.Provider>
}

export function useGoal(): GoalContextValue {
  const ctx = useContext(GoalContext)
  if (!ctx) {
    throw new Error('useGoal must be used within GoalProvider')
  }
  return ctx
}
