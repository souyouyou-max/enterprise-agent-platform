import React, { useState } from 'react'
import { Layout, Menu, Input, Button, Space, Tag, Typography, Tooltip } from 'antd'
import {
  HeartOutlined,
  FileTextOutlined,
  FileSearchOutlined,
  DiffOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  MessageOutlined,
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useConfig } from '@/store/config'
import { apiFetch } from '@/api/client'

const { Sider, Header, Content } = Layout
const { Text } = Typography

const menuItems = [
  {
    key: 'health',
    icon: <HeartOutlined />,
    label: '健康检查',
    path: '/health',
  },
  {
    key: 'texts',
    icon: <FileTextOutlined />,
    label: '文本对比',
    children: [
      { key: 'compare-two-texts', label: '两文本对比', path: '/compare-two-texts' },
      { key: 'compare-texts', label: '多文本对比', path: '/compare-texts' },
    ],
  },
  {
    key: 'files',
    icon: <FileSearchOutlined />,
    label: '文件对比',
    children: [
      { key: 'compare-two-files', label: '两文件对比', path: '/compare-two-files' },
      { key: 'compare-files', label: '多文件对比', path: '/compare-files' },
      { key: 'compare-base64', label: 'Base64 对比', path: '/compare-base64' },
    ],
  },
  {
    key: 'diff-viewer',
    icon: <DiffOutlined />,
    label: '文件 Diff 视图',
    path: '/diff-viewer',
  },
  {
    key: 'chat',
    icon: <MessageOutlined />,
    label: 'AI 对话',
    path: '/chat',
  },
]

const PAGE_TITLES: Record<string, string> = {
  '/health': '健康检查',
  '/compare-two-texts': '两文本对比',
  '/compare-texts': '多文本对比',
  '/compare-two-files': '两文件对比',
  '/compare-files': '多文件对比',
  '/compare-base64': 'Base64 文件对比',
  '/diff-viewer': '文件相似度 Diff 视图',
  '/chat': 'AI 对话',
}

type HealthStatus = 'unknown' | 'ok' | 'err'

export default function MainLayout({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { baseUrl, setBaseUrl } = useConfig()
  const [urlInput, setUrlInput] = useState(baseUrl)
  const [healthStatus, setHealthStatus] = useState<HealthStatus>('unknown')
  const [collapsed, setCollapsed] = useState(false)

  const activeKey = Object.entries(PAGE_TITLES).find(([path]) => location.pathname === path)?.[0] ?? '/health'
  const pageTitle = PAGE_TITLES[location.pathname] ?? 'Bid Analysis'

  const checkHealth = async () => {
    const url = urlInput.replace(/\/$/, '')
    setBaseUrl(url)
    try {
      await apiFetch(url, '/health')
      setHealthStatus('ok')
    } catch {
      setHealthStatus('err')
    }
  }

  const handleMenuClick = (path: string) => navigate(path)

  const buildMenuItems = () =>
    menuItems.map(item => {
      if (item.children) {
        return {
          key: item.key,
          icon: item.icon,
          label: item.label,
          children: item.children.map(c => ({
            key: c.path,
            label: c.label,
            onClick: () => handleMenuClick(c.path),
          })),
        }
      }
      return {
        key: item.path!,
        icon: item.icon,
        label: item.label,
        onClick: () => handleMenuClick(item.path!),
      }
    })

  const statusDot =
    healthStatus === 'ok' ? (
      <Tooltip title="服务正常"><CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} /></Tooltip>
    ) : healthStatus === 'err' ? (
      <Tooltip title="服务异常"><ExclamationCircleOutlined style={{ color: '#ff4d4f', fontSize: 16 }} /></Tooltip>
    ) : (
      <Tooltip title="未检测"><ApiOutlined style={{ color: '#8c8c8c', fontSize: 16 }} /></Tooltip>
    )

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        width={220}
        style={{ background: '#1a1d2e' }}
        theme="dark"
      >
        {!collapsed && (
          <div style={{ padding: '20px 18px 16px', borderBottom: '1px solid #2e3150' }}>
            <div style={{ fontWeight: 700, color: '#fff', fontSize: 15 }}>标书分析系统</div>
            <div style={{ fontSize: 11, color: '#666', marginTop: 3 }}>Bid Analysis Service</div>
          </div>
        )}
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[activeKey]}
          defaultOpenKeys={['texts', 'files']}
          items={buildMenuItems()}
          style={{ background: '#1a1d2e', borderRight: 'none', marginTop: collapsed ? 16 : 0 }}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            borderBottom: '1px solid #f0f0f0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            height: 56,
          }}
        >
          <Text strong style={{ fontSize: 16 }}>{pageTitle}</Text>
          <Space>
            {statusDot}
            <Input
              value={urlInput}
              onChange={e => setUrlInput(e.target.value)}
              onPressEnter={checkHealth}
              placeholder="http://localhost:8099"
              style={{ width: 220 }}
              size="small"
            />
            <Button size="small" onClick={checkHealth}>检测连通性</Button>
            <Tag color="blue" style={{ margin: 0 }}>v0.1</Tag>
          </Space>
        </Header>

        <Content style={{ padding: 24, background: '#f0f2f5', overflow: 'auto' }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  )
}
