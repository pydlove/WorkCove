export const DEFAULT_GIT_REPOSITORY_CATEGORY = '开发项目'

export type GitRepositorySource = 'local' | 'remote'

export interface GitRepository {
  id: string
  workspaceId: string
  name: string
  path: string
  remoteUrl: string
  source: GitRepositorySource
  category: string
  createdAt: string
  updatedAt: string
}

export interface GitRepositoryStatus {
  rootPath: string
  name: string
  branch: string
  remoteName: string
  remoteUrl: string
  ahead: number
  behind: number
  staged: number
  unstaged: number
  untracked: number
  conflicted: number
  clean: boolean
  latestCommitHash: string
  latestCommitShortHash: string
  latestCommitSubject: string
  latestCommitAt: string
  mergeInProgress: boolean
  changedFiles: GitChangedFile[]
  conflictFiles: string[]
}

export interface GitChangedFile {
  path: string
  indexStatus: string
  workTreeStatus: string
  staged: boolean
  unstaged: boolean
  untracked: boolean
  conflicted: boolean
}

export interface GitBranchInfo {
  name: string
  refName: string
  current: boolean
  remote: boolean
  upstream: string
  latestCommit: string
  updatedAt: string
}

export interface GitCommitInfo {
  hash: string
  shortHash: string
  parents: string
  authorName: string
  authorEmail: string
  authoredAt: string
  subject: string
}

export type GitOperationType =
  | 'FETCH'
  | 'PULL'
  | 'PUSH'
  | 'CREATE_BRANCH'
  | 'SWITCH_BRANCH'
  | 'DELETE_BRANCH'
  | 'MERGE'
  | 'ABORT_MERGE'
  | 'CONTINUE_MERGE'
  | 'STAGE_ALL'
  | 'COMMIT'

export interface GitOperationInput {
  path: string
  type: GitOperationType
  branchName?: string
  sourceBranch?: string
  commitMessage?: string
  force?: boolean
  setUpstream?: boolean
}

export interface GitOperationResult {
  success: boolean
  message: string
  output: string
  currentBranch: string
  mergeInProgress: boolean
  conflictFiles: string[]
}

export interface GitMonthStatistics {
  month: string
  commits: number
  additions: number
  deletions: number
}

export interface GitHourStatistics {
  hour: number
  commits: number
}

export interface GitAuthorStatistics {
  name: string
  email: string
  commits: number
  additions: number
  deletions: number
}

export interface GitRepositoryStatistics {
  since: string
  until: string
  totalCommits: number
  activeAuthors: number
  changedFiles: number
  additions: number
  deletions: number
  netLines: number
  totalLines: number
  trackedFiles: number
  months: GitMonthStatistics[]
  hours: GitHourStatistics[]
  authors: GitAuthorStatistics[]
}

export interface GitReviewContext {
  branch: string
  baseBranch: string
  baseCommit: string
  headCommit: string
  fromCommit: string
  commitCount: number
  fileCount: number
  additions: number
  deletions: number
  commits: GitCommitInfo[]
  changedFiles: string[]
}

export interface GitFileContent {
  revision: string
  filePath: string
  requestedLine: number
  startLine: number
  endLine: number
  totalLines: number
  lines: string[]
  truncatedBefore: boolean
  truncatedAfter: boolean
}
