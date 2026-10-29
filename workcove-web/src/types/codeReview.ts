export type CodeReviewStatus =
  | 'reviewing'
  | 'pending_manual'
  | 'passed'
  | 'issues'
  | 'failed'
  | 'repairing'
  | 'repair_completed'

export type CodeReviewFindingSeverity =
  | 'blocking'
  | 'warning'
  | 'suggestion'

export type CodeReviewFindingStatus =
  | 'pending'
  | 'confirmed'
  | 'dismissed'
  | 'ignored'

export interface CodeReviewFinding {
  id: string
  title: string
  file: string
  line: number | null
  severity: CodeReviewFindingSeverity
  needsManual: boolean
  reason: string
  suggestion: string
  status: CodeReviewFindingStatus
}

export interface CodeReviewRecord {
  id: string
  workspaceId: string
  repositoryId: string
  repositoryName: string
  repositoryPath: string
  branch: string
  branchLabel: string
  branchAuthor: string
  branchAuthorEmail: string
  reviewSince: string
  reviewUntil: string
  baseBranch: string
  baseCommit: string
  headCommit: string
  fromCommit: string
  commitCount: number
  fileCount: number
  changedFiles: string[]
  additions: number
  deletions: number
  focus: string
  status: CodeReviewStatus
  summary: string
  findings: CodeReviewFinding[]
  manualNote: string
  rawResult: string
  iteration: number
  previousReviewId: string | null
  codexSessionId: string
  codexTaskId: string
  repairSessionId: string
  repairTaskId: string
  createdAt: string
  updatedAt: string
  reviewedAt: string | null
}

export interface CodeReviewInput {
  workspaceId: string
  repositoryId: string
  repositoryName: string
  repositoryPath: string
  branch: string
  branchLabel: string
  branchAuthor: string
  branchAuthorEmail: string
  reviewSince: string
  reviewUntil: string
  baseBranch: string
  baseCommit: string
  headCommit: string
  fromCommit: string
  commitCount: number
  fileCount: number
  changedFiles: string[]
  additions: number
  deletions: number
  focus: string
  iteration: number
  previousReviewId?: string | null
}

export interface ParsedCodeReviewResult {
  verdict: 'pass' | 'manual'
  summary: string
  findings: CodeReviewFinding[]
}
