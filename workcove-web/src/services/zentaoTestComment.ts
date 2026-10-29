import { appendZentaoTestComment } from '@/api/zentao'
import type { ZentaoTestResult } from '@/types/zentaoTest'

export async function publishZentaoTestResultComment(
  result: ZentaoTestResult,
  options: { force?: boolean } = {},
) {
  if (result.status !== 'passed') {
    throw new Error('只有测试通过的 Codex 结果才能回写禅道')
  }
  if (result.zentaoNoteAt && !options.force) {
    return {
      alreadyExists: true,
      result,
    }
  }
  const response = await appendZentaoTestComment(result.bugId, {
    workspaceId: result.workspaceId,
    runId: result.runId,
    environment: result.environment,
    screenshots: result.screenshots,
    proof: [
      result.proof,
      '',
      `问题描述：${result.problemDescription}`,
    ].join('\n'),
  })
  return {
    alreadyExists: response.alreadyExists,
    result: {
      ...result,
      zentaoNoteAt: new Date().toISOString(),
      zentaoNoteError: '',
    },
  }
}

export function failedZentaoTestComment(
  result: ZentaoTestResult,
  error: unknown,
) {
  return {
    ...result,
    zentaoNoteError:
      error instanceof Error
        ? error.message
        : '同步测试说明到禅道失败',
  }
}
