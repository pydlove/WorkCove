import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type {
  ZentaoTestResult,
  ZentaoTestStatus,
} from '@/types/zentaoTest'

const STORAGE_KEY = 'workcove.zentao-test-results.v1'

function testStatus(value: unknown): ZentaoTestStatus {
  if (value === 'passed' || value === 'failed') return value
  return 'blocked'
}

function normalizeResult(value: unknown): ZentaoTestResult | null {
  if (!value || typeof value !== 'object') return null
  const result = value as Partial<ZentaoTestResult>
  if (
    typeof result.id !== 'string' ||
    typeof result.taskId !== 'string' ||
    typeof result.sessionId !== 'string' ||
    typeof result.runId !== 'string' ||
    typeof result.workspaceId !== 'string' ||
    typeof result.remoteId !== 'string' ||
    typeof result.bugId !== 'number'
  ) {
    return null
  }
  return {
    id: result.id,
    bugId: result.bugId,
    remoteId: result.remoteId,
    workspaceId: result.workspaceId,
    taskId: result.taskId,
    sessionId: result.sessionId,
    runId: result.runId,
    status: testStatus(result.status),
    environment:
      typeof result.environment === 'string'
        ? result.environment
        : '',
    problemDescription:
      typeof result.problemDescription === 'string'
        ? result.problemDescription
        : '',
    proof: typeof result.proof === 'string' ? result.proof : '',
    screenshots: Array.isArray(result.screenshots)
      ? result.screenshots.filter(
          (filename): filename is string =>
            typeof filename === 'string' && Boolean(filename.trim()),
        )
      : [],
    createdAt:
      typeof result.createdAt === 'string'
        ? result.createdAt
        : new Date().toISOString(),
  }
}

export function loadLocalZentaoTestResults(email: string) {
  const results = readUserScopedValue<ZentaoTestResult[]>(
    STORAGE_KEY,
    email,
  )
  return Array.isArray(results)
    ? results
        .map(normalizeResult)
        .filter((result): result is ZentaoTestResult => Boolean(result))
    : []
}

export function saveLocalZentaoTestResults(
  email: string,
  results: ZentaoTestResult[],
) {
  writeUserScopedValue(STORAGE_KEY, email, results)
}
