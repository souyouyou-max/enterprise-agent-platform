import React, { useEffect, useMemo, useRef, useState } from 'react'
import { Avatar, Button, Card, Divider, Flex, Input, Spin, Tag, Typography, Upload, message } from 'antd'
import { ApiOutlined, DeleteOutlined, LinkOutlined, PaperClipOutlined, RobotOutlined, SendOutlined, UserOutlined } from '@ant-design/icons'
import { toBase64 } from '@/api/client'
import { createChatSession, autoOcrChat, streamChat } from './chatApi'
import type { ChatMessage } from './types'
import styles from './ChatPage.module.css'

const { Text } = Typography
type Attachment = { name: string; base64: string; preview: string | null; mimeType: string }

function nowHm(ts: number) {
  const d = new Date(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

export default function ChatPage() {
  const [javaUrl, setJavaUrl] = useState('http://localhost:8079')
  const [urlInput, setUrlInput] = useState('http://localhost:8079')
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [loading, setLoading] = useState(false)
  const [sessionLoading, setSessionLoading] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    void resetSession(javaUrl)
  }, [])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const resetSession = async (url: string) => {
    setSessionLoading(true)
    try {
      const sid = await createChatSession(url)
      setSessionId(sid)
    } finally {
      setSessionLoading(false)
    }
  }

  const applyUrl = () => {
    const url = urlInput.replace(/\/$/, '')
    setJavaUrl(url)
    setMessages([])
    setSessionId(null)
    void resetSession(url)
  }

  const clearChat = () => {
    abortRef.current?.abort()
    abortRef.current = null
    setMessages([])
    clearAttachments()
  }

  const clearAttachments = () => {
    setAttachments(prev => {
      prev.forEach(item => {
        if (item.preview?.startsWith('blob:')) {
          URL.revokeObjectURL(item.preview)
        }
      })
      return []
    })
  }

  const sendMessage = async () => {
    if (loading || !sessionId) return
    if (!input.trim() && attachments.length === 0) return

    const userText = input.trim() || '描述一下图片'
    const hasImage = attachments.length > 0
    const currentAttachments = attachments
    setInput('')
    setAttachments([])
    setLoading(true)

    const userMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: userText,
      createdAt: Date.now(),
      imagePreview: currentAttachments[0]?.preview ?? undefined,
      attachmentName: currentAttachments[0]?.name ?? undefined,
      imagePreviews: currentAttachments.map(item => item.preview).filter((preview): preview is string => Boolean(preview)),
      attachmentNames: currentAttachments.map(item => item.name),
    }
    const assistantId = crypto.randomUUID()
    const assistantMsg: ChatMessage = {
      id: assistantId,
      role: 'assistant',
      content: '',
      createdAt: Date.now(),
      streaming: true,
    }
    setMessages(prev => [...prev, userMsg, assistantMsg])

    try {
      if (hasImage) {
        const payload = {
          text: userText,
          attachments: currentAttachments.map(item => ({
            name: item.name,
            mimeType: item.mimeType || 'application/octet-stream',
            base64: item.base64,
          })),
          user_info: {
            user_id: '1',
            user_name: '赵一名',
            user_dept_name: '互联网应用研发团队',
            user_company: '集团应用研发部',
          },
        } as const

        // 前端不再判断走哪个引擎，统一交给后端 auto-ocr 决策
        const result = await autoOcrChat(javaUrl, payload)

        setMessages(prev => prev.map(m => (
          m.id === assistantId ? { ...m, content: result } : m
        )))
      } else {
        abortRef.current?.abort()
        abortRef.current = new AbortController()
        await streamChat(javaUrl, sessionId, userText, (chunk) => {
          setMessages(prev => prev.map(m => (
            m.id === assistantId ? { ...m, content: m.content + chunk } : m
          )))
        }, abortRef.current.signal)
      }
    } catch (e) {
      setMessages(prev => {
        const msg = e instanceof Error ? e.message : '请求失败'
        return prev.map(m => (
          m.id === assistantId ? { ...m, content: `请求失败：${msg}`, streaming: false } : m
        ))
      })
    } finally {
      setMessages(prev => {
        return prev.map(m => (m.id === assistantId ? { ...m, streaming: false } : m))
      })
      setLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const handlePickImage = async (file: File) => {
    try {
      const base64 = await toBase64(file)
      const isImage = (file.type || '').startsWith('image/')
      const preview = isImage ? URL.createObjectURL(file) : null
      setAttachments(prev => [...prev, {
        name: file.name || '附件',
        base64,
        preview,
        mimeType: file.type || 'image/jpeg',
      }])
    } catch {
      message.error('图片读取失败，请重试')
    }
    return false
  }

  const clearImage = () => {
    clearAttachments()
  }

  const sessionTag = useMemo(() => {
    if (sessionLoading) return <Tag color="processing">初始化中…</Tag>
    if (!sessionId) return <Tag color="error">未连接</Tag>
    return <Tag color="success">会话 {sessionId.slice(0, 8)}…</Tag>
  }, [sessionId, sessionLoading])

  return (
    <div className={styles.page}>
      <Card
        size="small"
        className={styles.panel}
        title={(
          <Flex align="center" justify="space-between" wrap="wrap" gap={8}>
            <Flex align="center" gap={8}>
              <ApiOutlined />
              <Text strong>对话</Text>
              {sessionTag}
            </Flex>
            <Flex align="center" gap={8} wrap="wrap">
              <Input
                value={urlInput}
                onChange={e => setUrlInput(e.target.value)}
                onPressEnter={applyUrl}
                placeholder="http://localhost:8079"
                style={{ width: 320 }}
                size="small"
                prefix={<LinkOutlined />}
              />
              <Button size="small" onClick={applyUrl}>连接</Button>
              <Button size="small" icon={<DeleteOutlined />} onClick={clearChat} disabled={messages.length === 0}>
                清空
              </Button>
            </Flex>
          </Flex>
        )}
        styles={{ body: { display: 'flex', flexDirection: 'column', minHeight: 0, flex: 1 } }}
      >
        <div className={styles.messageArea}>
          {messages.length === 0 && (
            <div style={{ textAlign: 'center', padding: '64px 12px', color: 'rgba(0,0,0,0.45)' }}>
              <RobotOutlined style={{ fontSize: 44, marginBottom: 10 }} />
              <div style={{ fontSize: 16, fontWeight: 600, color: 'rgba(0,0,0,0.75)' }}>开始一段对话</div>
              <div style={{ marginTop: 6 }}>Enter 发送，Shift+Enter 换行</div>
              <Divider style={{ margin: '18px 0 0' }} />
              <div style={{ marginTop: 12, maxWidth: 520, marginInline: 'auto', textAlign: 'left' }}>
                <Text type="secondary">示例：</Text>
                <div style={{ marginTop: 6 }}>
                  <Tag>帮我分析机构 12345 的风险</Tag>
                  <Tag>总结一下今天的工作要点</Tag>
                  <Tag>用更清晰的语言改写这段话</Tag>
                </div>
              </div>
            </div>
          )}

          {messages.map((m) => {
            const isUser = m.role === 'user'
            return (
              <div
                key={m.id}
                className={[
                  styles.row,
                  isUser ? styles.rowUser : styles.rowAssistant,
                ].join(' ')}
              >
                {!isUser && (
                  <Avatar
                    size={32}
                    icon={<RobotOutlined />}
                    style={{ background: '#5b7cfa', flexShrink: 0 }}
                  />
                )}

                <div className={[
                  styles.bubble,
                  isUser ? styles.bubbleUser : styles.bubbleAssistant,
                ].join(' ')}>
                  {m.imagePreviews && m.imagePreviews.length > 0 && (
                    <div className={styles.bubbleImageList}>
                      {m.imagePreviews.map((src, idx) => (
                        <img key={`${m.id}-${idx}`} src={src} alt="uploaded" className={styles.bubbleImage} />
                      ))}
                    </div>
                  )}
                  {(!m.imagePreviews || m.imagePreviews.length === 0) && m.attachmentNames && m.attachmentNames.length > 0 && (
                    <div className={styles.attachmentTagList}>
                      {m.attachmentNames.map((name, idx) => (
                        <Tag key={`${m.id}-${idx}`} style={{ marginBottom: 6 }}>{name}</Tag>
                      ))}
                    </div>
                  )}
                  {m.content || (m.streaming ? <Spin size="small" /> : '')}
                  {m.streaming && m.content && <span className={styles.cursor} />}
                  <div className={styles.meta}>
                    <Text type="secondary">{nowHm(m.createdAt)}</Text>
                  </div>
                </div>

                {isUser && (
                  <Avatar
                    size={32}
                    icon={<UserOutlined />}
                    style={{ background: '#e6e8ff', color: '#5b7cfa', flexShrink: 0 }}
                  />
                )}
              </div>
            )
          })}
          <div ref={bottomRef} />
        </div>

        {attachments.length > 0 && (
          <div className={styles.attachmentBar}>
            <div className={styles.attachmentThumbList}>
              {attachments.map((item, idx) => (
                item.preview
                  ? <img key={`${item.name}-${idx}`} src={item.preview} alt="preview" className={styles.attachmentThumb} />
                  : <Tag key={`${item.name}-${idx}`} color="processing">{item.name}</Tag>
              ))}
            </div>
            <Tag color="processing">已选择 {attachments.length} 个附件，发送时将走图文识别（文档将按页转图）</Tag>
            <Button size="small" onClick={clearImage}>移除</Button>
          </div>
        )}

        <div className={styles.composer}>
          <Upload
            showUploadList={false}
            beforeUpload={handlePickImage}
            multiple
            disabled={loading || !sessionId}
          >
            <Button icon={<PaperClipOutlined />} style={{ height: 42, borderRadius: 10 }}>
              上传文件
            </Button>
          </Upload>
          <Input.TextArea
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={sessionId ? '输入问题（可选），可搭配图片/文档一起发送' : '未连接，先点击右上角连接'}
            autoSize={{ minRows: 1, maxRows: 6 }}
            style={{ borderRadius: 10 }}
            disabled={loading || !sessionId}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={sendMessage}
            loading={loading}
            disabled={!sessionId || (!input.trim() && attachments.length === 0)}
            style={{ height: 42, borderRadius: 10, paddingInline: 16 }}
          >
            发送
          </Button>
        </div>
      </Card>
    </div>
  )
}
