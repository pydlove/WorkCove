import type { ZentaoBug } from '@/types/zentao'
import type { ZentaoTestResult } from '@/types/zentaoTest'

export function shouldAutoRepairZentaoTest(
  result: ZentaoTestResult,
) {
  return result.status !== 'passed'
}

export function zentaoTestRepairTitle(
  bug: ZentaoBug | null,
  result: ZentaoTestResult,
) {
  const remoteId = bug?.remoteId || result.remoteId
  const suffix = bug?.title?.trim() || result.problemDescription
  return `修复测试未通过：Bug #${remoteId} ${suffix}`.slice(0, 200)
}

export function zentaoTestRepairPrompt(
  bug: ZentaoBug | null,
  result: ZentaoTestResult,
) {
  const remoteId = bug?.remoteId || result.remoteId
  const title = bug?.title?.trim() || result.problemDescription
  const evidence = [
    result.proof.trim(),
    result.screenshots.length > 0
      ? `测试截图：${result.screenshots.join('、')}`
      : '',
  ]
    .filter(Boolean)
    .join('\n')
    .slice(0, 6_000)

  return [
    `Codex 测试没有通过，请修复 Bug #${remoteId}：${title}`,
    '',
    `测试环境：${result.environment}`,
    `测试状态：${result.status === 'blocked' ? '测试阻塞' : '测试失败'}`,
    `问题描述：${result.problemDescription}`,
    '',
    '测试证据：',
    evidence || '测试结果中没有提供更多证据。',
    '',
    ...(bug?.steps
      ? ['复现步骤：', bug.steps.slice(0, 6_000), '']
      : []),
    '处理要求：',
    '1. 先判断失败原因是代码缺陷、测试环境/登录问题，还是验证方式不正确。',
    '2. 如果是代码缺陷，定位根因，完成代码修复并运行相关测试或构建验证。',
    '3. 如果是环境或测试方式问题，不得伪造修复结果，请明确阻塞原因和需要补充的条件。',
    '4. 不要修改测试截图或伪造成通过结果，不要自动 push 或覆盖远端分支。',
    '5. 完成后说明实际修改、验证结果和剩余风险，并更新该 Bug 的本地解决方案与定位原因。',
    '',
    '最后必须按以下格式输出：',
    '解决方案：具体的修复或处理结论',
    '定位原因：根因、环境原因或测试阻塞原因',
    '验证结果：实际执行的验证及结果',
  ].join('\n')
}
