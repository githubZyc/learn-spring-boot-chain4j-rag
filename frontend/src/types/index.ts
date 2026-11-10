export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export interface DocumentItem {
  id: string
  fileName: string
  fileType: string
  sizeBytes: number
  status: DocumentStatus
  chunkCount: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface Source {
  index: number
  docId: string
  fileName: string
  chunkIndex: number | null
  score: number
  snippet: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  sources: Source[]
  error?: string
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
}
