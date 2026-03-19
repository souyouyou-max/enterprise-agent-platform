export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: number
  streaming?: boolean
  imagePreview?: string
  attachmentName?: string
  imagePreviews?: string[]
  attachmentNames?: string[]
}

