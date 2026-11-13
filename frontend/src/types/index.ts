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
  vectorScore: number
  rankReason: string
  snippet: string
}

export interface SchemaAskResponse {
  question: string
  matches: SchemaTableMatch[]
}

export interface SqlTableRef {
  tableName: string
  tableComment: string
}

export interface SqlQueryResult {
  question: string
  tables: SqlTableRef[]
  sql: string
  columns: string[]
  rows: (string | null)[][]
  rowCount: number
  costMs: number
  maskedColumns: string[]
}

export type QcSeverity = 'CRITICAL' | 'WARNING' | 'INFO'
export type QcFindingStatus = 'OPEN' | 'ACK' | 'RESOLVED' | 'IGNORED'
export type QcStageStatus = 'DONE' | 'IN_PROGRESS' | 'PENDING' | 'MISSING'

export interface QcFinding {
  id: number
  ruleCode: string
  ruleName: string
  severity: QcSeverity
  status: QcFindingStatus
  recordId: number
  recordNo: string
  departId: string
  departName: string
  doctorId: string
  doctorName: string
  surgeryDate: string
  title: string
  detail: string
  evidence: Record<string, unknown>
  owner: string | null
  firstFoundAt: string
  lastFoundAt: string
  resolvedAt: string | null
  resolvedBy: string | null
  resolveNote: string | null
}

export interface QcPage<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface QcDepartRank {
  departId: string
  departName: string
  cases: number
  findings: number
  archived: number
  archiveRate: number
  avgArchiveDays: number
}

export interface QcOverview {
  from: string
  to: string
  departId: string | null
  totalCases: number
  shouldHaveData: number
  archived: number
  abnormalTerminated: number
  staleInProgress: number
  openFindings: number
  criticalFindings: number
  archiveRate: number
  avgArchiveDays: number
  findingsByRule: Record<string, number>
  findingsBySeverity: Record<string, number>
  departRanking: QcDepartRank[]
  topFindings: QcFinding[]
}

export interface QcSetup {
  operatingRoomName: string | null
  extractionNeedle: string | null
  extractionArea: string | null
  difficultyFactorName: string | null
  mentorApprentice: string | null
  mentorApprenticeSon: string | null
  transplantTechnique: string | null
  videoFileIds: string | null
}

export interface QcAnesthesia {
  startTime: string | null
  endTime: string | null
  durationMinutes: number | null
  videoFileIds: string | null
  operatorName: string | null
  remark: string | null
}

export interface QcExtraction {
  extractionStartTime: string | null
  extractionEndTime: string | null
  extractionOnly: boolean
  extractionCount: number | null
  regularSeparationStartTime: string | null
  regularSeparationEndTime: string | null
  regularSeparationSingle: number | null
  regularSeparationMulti: number | null
  longSeparationStartTime: string | null
  longSeparationEndTime: string | null
  longSeparationSingle: number | null
  longSeparationMulti: number | null
  videoFileIds: string | null
  nurseName: string | null
}

export interface QcPlanting {
  startTime: string | null
  endTime: string | null
  regularPlantingSingle: number | null
  regularPlantingMulti: number | null
  regularPlantingPosition: string | null
  longPlantingSingle: number | null
  longPlantingMulti: number | null
  longPlantingPosition: string | null
  videoFileIds: string | null
  nurseName: string | null
}

export interface QcCaseInfo {
  id: number
  recordNo: string
  departId: string
  departName: string
  surgeryDate: string
  currentStage: string
  qcStatus: number
  transplantTechnique: string
  operationId: string
  qcNurseId: string
  qcNurseName: string
  accountId: string
  accountCode: string
  accountName: string
  caseId: string
  dzblNo: string
  chargeOrderId: string
  surgeryStartTime: string | null
  surgeryEndTime: string | null
  filedTime: string | null
  filedBy: string | null
  setup: QcSetup | null
  anesthesia: QcAnesthesia | null
  extraction: QcExtraction | null
  planting: QcPlanting | null
}

export interface QcStage {
  code: string
  name: string
  status: QcStageStatus
  start: string | null
  end: string | null
  durationMinutes: number | null
}

export interface QcCaseDetail {
  qcCase: QcCaseInfo
  findings: QcFinding[]
  stages: QcStage[]
  missingStages: string[]
}

export interface QcRule {
  code: string
  name: string
  severity: QcSeverity
  enabled: boolean
}

export interface QcScanResult {
  totalCases: number
  totalFindings: number
  created: number
  updated: number
  reopened: number
  closed: number
  costMs: number
  scannedAt: string
}

export interface QcFindingFilters {
  status: QcFindingStatus | ''
  severity: QcSeverity | ''
  ruleCode: string
  departId: string
  page: number
  size: number
}
