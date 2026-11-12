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

export type SchemaSyncStatus = 'IDLE' | 'RUNNING' | 'READY' | 'FAILED'

export interface SchemaStatus {
  configured: boolean
  database: string | null
  collection: string
  status: SchemaSyncStatus
  tableCount: number
  error: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface SchemaTableMatch {
  tableName: string
  tableComment: string
  defaultTimeColumn: string
  sensitiveColumns: string[]
  score: number
  snippet: string
}

export interface SchemaAskResponse {
  question: string
  matches: SchemaTableMatch[]
}
