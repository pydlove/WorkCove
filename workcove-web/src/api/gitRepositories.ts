import { postJsonWithHeaders } from '@/api/http'
import type {
  GitBranchInfo,
  GitCommitInfo,
  GitOperationInput,
  GitOperationResult,
  GitRepositoryStatus,
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

export function executeGitOperation(input: GitOperationInput) {
  return postJsonWithHeaders<GitOperationResult>(
    '/development/git/execute',
    input,
    ACTION_HEADER,
    160_000,
  )
}
