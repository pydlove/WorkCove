import { postJsonWithHeaders } from '@/api/http'
import type {
  GitBranchInfo,
  GitCommitInfo,
  GitFileContent,
  GitOperationInput,
  GitOperationResult,
  GitRepositoryStatistics,
  GitRepositoryStatus,
  GitReviewContext,
} from '@/types/gitRepository'

const ACTION_HEADER = {
  'X-WorkCove-Action': 'manage-git-repositories',
}

export function inspectGitRepository(path: string) {
  return postJsonWithHeaders<GitRepositoryStatus>(
    '/development/git/inspect',
    { path },
    ACTION_HEADER,
    20_000,
  )
}

export function listGitBranches(path: string) {
  return postJsonWithHeaders<GitBranchInfo[]>(
    '/development/git/branches',
    { path },
    ACTION_HEADER,
    30_000,
  )
}

export function listGitCommits(path: string, limit = 100) {
  return postJsonWithHeaders<GitCommitInfo[]>(
    '/development/git/log',
    { path, limit },
    ACTION_HEADER,
    30_000,
  )
}

export function listGitAuthorCommits(
  path: string,
  authorName: string,
  authorEmail: string,
) {
  return postJsonWithHeaders<GitCommitInfo[]>(
    '/development/git/author-log',
    {
      path,
      name: authorName,
      email: authorEmail,
    },
    ACTION_HEADER,
    180_000,
  )
}

export function getGitStatistics(path: string) {
  return postJsonWithHeaders<GitRepositoryStatistics>(
    '/development/git/statistics',
    { path },
    ACTION_HEADER,
    180_000,
  )
}

export function getGitReviewContext(input: {
  path: string
  branch: string
  baseBranch: string
  fromCommit?: string
  since?: string
  until?: string
}) {
  return postJsonWithHeaders<GitReviewContext>(
    '/development/git/review-context',
    input,
    ACTION_HEADER,
    60_000,
  )
}

export function getGitFileContent(input: {
  path: string
  revision: string
  filePath: string
  line?: number | null
}) {
  return postJsonWithHeaders<GitFileContent>(
    '/development/git/file-content',
    input,
    ACTION_HEADER,
    30_000,
  )
}

export function executeGitOperation(input: GitOperationInput) {
  return postJsonWithHeaders<GitOperationResult>(
    '/development/git/execute',
    input,
    ACTION_HEADER,
    160_000,
  )
}
