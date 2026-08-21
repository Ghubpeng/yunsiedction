import { lazy, Suspense, type ComponentType, type ReactNode } from 'react'
import { createBrowserRouter, Navigate, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from '../shared/auth/AuthContext'
import { PermissionProvider } from '../shared/auth/PermissionContext'
import { GoalProvider, useGoal } from '../shared/learn/GoalContext'
import { AppLayout } from '../shared/layout/AppLayout'
import { PublicLayout } from '../shared/layout/PublicLayout'
import { AdminLayout } from '../admin/AdminLayout'
import { RequireAdmin } from '../admin/access'
import { Spinner } from '../shared/ui/primitives'

// 页面级路由懒加载（frontend-development §4.5 强制）
function lazyPage(loader: () => Promise<{ default: ComponentType }>) {
  return lazy(loader)
}

const GoalOnboardingPage = lazyPage(() => import('../features/learn/GoalOnboardingPage'))
const ServicePage = lazyPage(() => import('../features/service/ServicePage'))
const LandingPage = lazyPage(() => import('../features/home/LandingPage'))
const LoginPage = lazyPage(() => import('../features/auth/LoginPage'))
const HomePage = lazyPage(() => import('../features/home/HomePage'))
const ChapterPracticePage = lazyPage(() => import('../features/course/ChapterPracticePage'))
const CourseListPage = lazyPage(() => import('../features/course/CourseListPage'))
const CourseDetailPage = lazyPage(() => import('../features/course/CourseDetailPage'))
const LessonPlayerPage = lazyPage(() => import('../features/course/LessonPlayerPage'))
const PracticePage = lazyPage(() => import('../features/practice/PracticePage'))
const MistakesPage = lazyPage(() => import('../features/practice/MistakesPage'))
const ExamListPage = lazyPage(() => import('../features/exam/ExamListPage'))
const ExamSessionPage = lazyPage(() => import('../features/exam/ExamSessionPage'))
const ExamResultPage = lazyPage(() => import('../features/exam/ExamResultPage'))
const ProfilePage = lazyPage(() => import('../features/learn/ProfilePage'))
const LearningPathPage = lazyPage(() => import('../features/learn/LearningPathPage'))
const MessagesPage = lazyPage(() => import('../features/notify/MessagesPage'))

// 管理端页面
const AdminDashboardPage = lazyPage(() => import('../features/admin/AdminDashboardPage'))
const AdminUsersPage = lazyPage(() => import('../features/admin/AdminUsersPage'))
const AdminRbacPage = lazyPage(() => import('../features/admin/AdminRbacPage'))
const AdminCertificatesPage = lazyPage(() => import('../features/admin/AdminCertificatesPage'))
const AdminSubjectsPage = lazyPage(() => import('../features/admin/AdminSubjectsPage'))
const AdminQuestionsPage = lazyPage(() => import('../features/admin/AdminQuestionsPage'))
const AdminQuestionImportPage = lazyPage(() => import('../features/admin/AdminQuestionImportPage'))
const AdminExamsPage = lazyPage(() => import('../features/admin/AdminExamsPage'))
const AdminExamDetailPage = lazyPage(() => import('../features/admin/AdminExamDetailPage'))
const AdminCoursesPage = lazyPage(() => import('../features/admin/AdminCoursesPage'))
const AdminCourseDetailPage = lazyPage(() => import('../features/admin/AdminCourseDetailPage'))
const AdminStudentsPage = lazyPage(() => import('../features/admin/AdminStudentsPage'))
const AdminServiceConfigPage = lazyPage(() => import('../features/admin/AdminServiceConfigPage'))

const RouteFallback = (
  <div className="page">
    <Spinner />
  </div>
)

function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, booting } = useAuth()
  const location = useLocation()
  if (booting) {
    return (
      <div className="page">
        <Spinner label="正在进入…" />
      </div>
    )
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  return <>{children}</>
}

/**
 * 用户端外壳：匿名 → PublicLayout（公开浏览/登录/Demo），登录 → AppLayout（学习应用）。
 * Stage 2.1：打开首页不再被登录墙拦截，公开内容先行，个人数据才要求登录。
 */
function UserShell() {
  const { isAuthenticated, booting } = useAuth()
  if (booting) {
    return (
      <div className="page">
        <Spinner label="正在进入…" />
      </div>
    )
  }
  return isAuthenticated ? <AppLayout /> : <PublicLayout />
}

/** 首页：匿名 → 公开 Landing；登录 → 学习首页（无考试目标先引导选择） */
function HomeRoute() {
  const { isAuthenticated } = useAuth()
  const { goal, loading } = useGoal()
  if (!isAuthenticated) return <LandingPage />
  if (!loading && !goal) return <GoalOnboardingPage />
  return <HomePage />
}

function LoginRoute() {
  const { isAuthenticated, booting } = useAuth()
  if (booting) {
    return (
      <div className="page">
        <Spinner />
      </div>
    )
  }
  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }
  return <LoginPage />
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <Suspense fallback={RouteFallback}>
        <LoginRoute />
      </Suspense>
    ),
  },
  {
    // 联系客服（公开页：登录前后均可访问）
    path: '/service',
    element: (
      <Suspense fallback={RouteFallback}>
        <PublicLayout>
          <ServicePage />
        </PublicLayout>
      </Suspense>
    ),
  },
  {
    path: '/',
    element: (
      <Suspense fallback={RouteFallback}>
        <UserShell />
      </Suspense>
    ),
    children: [
      { index: true, element: <HomeRoute /> },
      // 公开可浏览（匿名看大纲；登录后带个人进度）
      { path: 'courses', element: <CourseListPage /> },
      { path: 'courses/:courseId', element: <CourseDetailPage /> },
      {
        path: 'goal-select',
        element: (
          <RequireAuth>
            <GoalOnboardingPage />
          </RequireAuth>
        ),
      },
      // 个人数据：必须登录
      {
        path: 'courses/:courseId/lessons/:lessonId',
        element: (
          <RequireAuth>
            <LessonPlayerPage />
          </RequireAuth>
        ),
      },
      {
        path: 'courses/:courseId/chapters/:chapterId/practice',
        element: (
          <RequireAuth>
            <ChapterPracticePage />
          </RequireAuth>
        ),
      },
      {
        path: 'practice',
        element: (
          <RequireAuth>
            <PracticePage />
          </RequireAuth>
        ),
      },
      {
        path: 'mistakes',
        element: (
          <RequireAuth>
            <MistakesPage />
          </RequireAuth>
        ),
      },
      {
        path: 'exams',
        element: (
          <RequireAuth>
            <ExamListPage />
          </RequireAuth>
        ),
      },
      {
        path: 'exams/:examId/session',
        element: (
          <RequireAuth>
            <ExamSessionPage />
          </RequireAuth>
        ),
      },
      {
        path: 'exams/results/:attemptId',
        element: (
          <RequireAuth>
            <ExamResultPage />
          </RequireAuth>
        ),
      },
      {
        path: 'profile',
        element: (
          <RequireAuth>
            <ProfilePage />
          </RequireAuth>
        ),
      },
      {
        path: 'learn-path',
        element: (
          <RequireAuth>
            <LearningPathPage />
          </RequireAuth>
        ),
      },
      {
        path: 'messages',
        element: (
          <RequireAuth>
            <MessagesPage />
          </RequireAuth>
        ),
      },
    ],
  },
  {
    path: '/admin',
    element: (
      <RequireAuth>
        <RequireAdmin>
          <AdminLayout />
        </RequireAdmin>
      </RequireAuth>
    ),
    children: [
      { index: true, element: <AdminDashboardPage /> },
      { path: 'users', element: <AdminUsersPage /> },
      { path: 'rbac', element: <AdminRbacPage /> },
      { path: 'certificates', element: <AdminCertificatesPage /> },
      { path: 'subjects', element: <AdminSubjectsPage /> },
      { path: 'questions', element: <AdminQuestionsPage /> },
      { path: 'questions/import', element: <AdminQuestionImportPage /> },
      { path: 'exams', element: <AdminExamsPage /> },
      { path: 'exams/:id', element: <AdminExamDetailPage /> },
      { path: 'courses', element: <AdminCoursesPage /> },
      { path: 'courses/:id', element: <AdminCourseDetailPage /> },
      { path: 'students', element: <AdminStudentsPage /> },
      { path: 'service', element: <AdminServiceConfigPage /> },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
])

export function RootProviders({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <PermissionProvider>
        <GoalProvider>
          <Suspense fallback={RouteFallback}>{children}</Suspense>
        </GoalProvider>
      </PermissionProvider>
    </AuthProvider>
  )
}
