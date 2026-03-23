import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider as AntdConfig } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { ConfigProvider } from '@/store/config'
import MainLayout from '@/layouts/MainLayout'
import HealthPage from '@/pages/Health'
import CompareTwoTextsPage from '@/pages/CompareTwoTexts'
import CompareTextsPage from '@/pages/CompareTexts'
import CompareTwoFilesPage from '@/pages/CompareTwoFiles'
import CompareFilesPage from '@/pages/CompareFiles'
import CompareBase64Page from '@/pages/CompareBase64'
import DiffViewerPage from '@/pages/DiffViewer'
import ChatPage from '@/pages/Chat'
import OcrPreviewPage from '@/pages/OcrPreview'
import OcrPipelinePage from '@/pages/OcrPipeline'

export default function App() {
  return (
    <AntdConfig
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#4f6df5',
          borderRadius: 12,
          colorBgLayout: '#f5f8ff',
          colorText: '#1f2937',
        },
        components: {
          Card: {
            borderRadiusLG: 16,
            headerHeight: 56,
          },
          Button: {
            borderRadius: 10,
            controlHeight: 38,
          },
          Input: {
            borderRadius: 10,
            controlHeight: 38,
          },
        },
      }}
    >
      <ConfigProvider>
        <BrowserRouter>
          <MainLayout>
            <Routes>
              <Route path="/" element={<Navigate to="/health" replace />} />
              <Route path="/health" element={<HealthPage />} />
              <Route path="/compare-two-texts" element={<CompareTwoTextsPage />} />
              <Route path="/compare-texts" element={<CompareTextsPage />} />
              <Route path="/compare-two-files" element={<CompareTwoFilesPage />} />
              <Route path="/compare-files" element={<CompareFilesPage />} />
              <Route path="/compare-base64" element={<CompareBase64Page />} />
              <Route path="/diff-viewer" element={<DiffViewerPage />} />
              <Route path="/chat" element={<ChatPage />} />
              <Route path="/ocr-preview" element={<OcrPreviewPage />} />
              <Route path="/ocr-pipeline" element={<OcrPipelinePage />} />
            </Routes>
          </MainLayout>
        </BrowserRouter>
      </ConfigProvider>
    </AntdConfig>
  )
}
