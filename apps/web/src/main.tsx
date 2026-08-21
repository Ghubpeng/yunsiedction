import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { router, RootProviders } from './app/router'
import { ErrorBoundary } from './app/ErrorBoundary'
import { ToastProvider } from './shared/ui/Toast'
import './styles/tokens.css'
import './styles/components.css'
import './styles/layout.css'
import './styles/features.css'
import './styles/editorial.css'
import './styles/admin.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <ToastProvider>
        <RootProviders>
          <RouterProvider router={router} />
        </RootProviders>
      </ToastProvider>
    </ErrorBoundary>
  </StrictMode>,
)
