const GIT_POLICY_MARKER = '【Git 分支约束】'
const BRANCH_NAME_PATTERN = /^[\p{L}\p{N}._/-]+$/u

export function validateCodexDevelopmentBranchName(value: string) {
  const name = value.trim()
  if (!name) return '请输入分支名称'
  if ([...name].length > 120) return '分支名称不能超过 120 个字符'
  if (!BRANCH_NAME_PATTERN.test(name)) {
    return '分支名称只能包含文字、数字、点、下划线、斜杠和短横线'
  }
  if (
    name.startsWith('-') ||
    name.startsWith('/') ||
    name.endsWith('/') ||
    name.endsWith('.') ||
    name.includes('..') ||
    name.includes('//') ||
    name.includes('@{')
  ) {
    return '分支名称格式无效'
  }
  if (
    name.split('/').some(
      (segment) =>
        !segment ||
        segment.startsWith('.') ||
        segment.endsWith('.lock'),
    )
  ) {
    return '分支名称格式无效'
  }
  return ''
}

export function applyCodexGitBranchPolicy(
  prompt: string,
  options: {
    currentBranch?: string
    createBranch?: string
  } = {},
) {
  const content = prompt.trim()
  if (content.includes(GIT_POLICY_MARKER)) return content

  const currentBranch = options.currentBranch?.trim() ?? ''
  const createBranch = options.createBranch?.trim() ?? ''
  if (createBranch) {
    const validation = validateCodexDevelopmentBranchName(createBranch)
    if (validation) throw new Error(validation)
    return joinPromptLines([
      GIT_POLICY_MARKER,
      '本次用户已通过“新增分支开发”明确授权创建分支。',
      currentBranch ? `当前分支：${currentBranch}` : '',
      `目标分支：${createBranch}`,
      '',
      '【分支操作要求】',
      '1. 开始修改前先检查当前 Git 仓库、工作区状态、当前分支和目标分支。',
      `2. 仅允许从当前 HEAD 创建并切换到「${createBranch}」。使用带明确参数的安全命令，例如 git switch -c "${createBranch}"。`,
      '3. 如果当前目录不是 Git 仓库、目标分支已存在或状态不安全，立即停止并说明原因。不得删除、重置、覆盖或复用已有分支。',
      '4. 创建后所有修改和验证都必须留在该分支，不得再创建其他分支或 worktree。',
      '5. 未收到明确要求时，不要 push、merge、rebase、打标签或删除分支。',
      '',
      '【用户任务】',
      content,
    ])
  }

  return joinPromptLines([
    GIT_POLICY_MARKER,
    '本任务没有“新增分支开发”授权。',
    currentBranch ? `必须继续在当前分支「${currentBranch}」执行。` : '',
    '',
    '【强制要求】',
    '1. 保持在当前工作目录的当前分支上完成修改、验证和测试。',
    '2. 不得创建、切换、重命名或删除分支，不得创建 worktree。',
    '3. 禁止执行 git switch -c、git checkout -b、git branch <新分支> 等创建分支命令。',
    '4. 本约束优先于仓库文档中的 Git Flow、分支命名和隔离建议。',
    '5. 不要自行选择“更安全”的新分支；如果当前分支无法继续，停止并说明原因。',
    '',
    '【用户任务】',
    content,
  ])
}

function joinPromptLines(lines: string[]) {
  return lines
    .filter(
      (line, index) =>
        Boolean(line) ||
        (index > 0 && Boolean(lines[index - 1])),
    )
    .join('\n')
}
