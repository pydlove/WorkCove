import type {
  CodeReviewFinding,
  CodeReviewFindingSeverity,
  CodeReviewRecord,
  ParsedCodeReviewResult,
} from '@/types/codeReview'

const HIGH_RISK_FILE =
  /(^|\/)(auth|security|permission|permissions|payment|billing|migration|migrations)(\/|\.|$)|\.sql$|application[^/]*\.ya?ml$|(^|\/)package\.json$|(^|\/)pom\.xml$/i

export function codeReviewPrompt(record: CodeReviewRecord) {
  return [
    '【代码评审任务】',
    `仓库：${record.repositoryName}`,
    `仓库路径：${record.repositoryPath}`,
    `评审分支：${record.branchLabel}`,
    `对比分支：${record.baseBranch}`,
    `共同祖先：${record.baseCommit}`,
    `评审起点：${record.fromCommit}`,
    `评审终点：${record.headCommit}`,
    `提交时间范围：${record.reviewSince || '不限'} 至 ${
      record.reviewUntil || '当前'
    }`,
    `本次范围：${record.commitCount} 个提交，${record.fileCount} 个文件，+${record.additions}/-${record.deletions}`,
    `变更文件：${
      previewFiles(record.changedFiles ?? []) || '请从 Git 差异中读取'
    }`,
    '',
    '【评审定位】',
    '这是一次已经提交到分支后的代码初审，不是合入审批流程。',
    '你的任务是发现真正需要人工判断的问题，减少人工逐行阅读大 Diff 的成本。',
    '',
    '【检查要求】',
    `1. 使用 Git 检查 ${record.fromCommit}..${record.headCommit} 的实际差异，不要只根据提交说明判断。`,
    '2. 重点检查正确性、异常处理、并发、安全、权限、数据库兼容、API 契约、资源释放、测试缺失和明显回归。',
    '3. 忽略锁文件、构建产物、纯格式化、自动生成代码和无关文件。',
    '4. 不要修改代码，不要提交，不要切换分支，不要执行会改变工作区的命令。',
    '5. 只报告有代码证据的问题。没有把握、需要业务判断或高风险区域必须标记为需要人工审核。',
    '6. 认证、权限、安全、支付、数据库迁移、发布配置等高风险文件的修改必须进入人工审核。',
    '7. 如果无法完整读取差异，必须要求人工审核，不能因为代码量大而直接通过。',
    record.focus.trim()
      ? `8. 本次额外关注：${record.focus.trim()}`
      : '',
    '',
    '【输出要求】',
    '完成检查后，只保留一个结果块，严格使用下面的 XML 标记和 JSON 格式；JSON 中不要使用注释或 Markdown 代码围栏：',
    '<CODE_REVIEW_RESULT>',
    '{',
    '  "verdict": "pass 或 manual",',
    '  "summary": "用 1-3 句话说明评审范围和主要结论",',
    '  "findings": [',
    '    {',
    '      "title": "简短问题标题",',
    '      "file": "相对仓库路径",',
    '      "line": 123,',
    '      "severity": "blocking | warning | suggestion",',
    '      "needsManual": true,',
    '      "reason": "具体原因和代码证据",',
    '      "suggestion": "建议的修改或人工核查方式"',
    '    }',
    '  ]',
    '}',
    '</CODE_REVIEW_RESULT>',
    '',
    '只有所有改动均未发现需要人工介入的问题时，verdict 才能是 pass。',
    '存在 blocking 问题、把握不足、高风险文件或需要业务确认时，verdict 必须是 manual。',
  ]
    .filter(Boolean)
    .join('\n')
}

export function parseCodeReviewResult(
  raw: string,
  changedFiles: string[] = [],
): ParsedCodeReviewResult | null {
  const match = raw.match(
    /<CODE_REVIEW_RESULT>\s*([\s\S]*?)\s*<\/CODE_REVIEW_RESULT>/i,
  )
  if (!match) return null
  let value: unknown
  try {
    value = JSON.parse(match[1].trim())
  } catch {
    return null
  }
  if (!value || typeof value !== 'object') return null
  const payload = value as Record<string, unknown>
  const verdict =
    payload.verdict === 'pass' ? 'pass' : payload.verdict === 'manual'
      ? 'manual'
      : null
  if (!verdict) return null
  const summary =
    typeof payload.summary === 'string'
      ? payload.summary.trim().slice(0, 4000)
      : ''
  const findings = Array.isArray(payload.findings)
    ? payload.findings
        .map((finding, index) => normalizeFinding(finding, index))
        .filter((finding): finding is CodeReviewFinding =>
          Boolean(finding),
        )
        .slice(0, 80)
    : []
  const coveredFiles = new Set(findings.map((finding) => finding.file))
  for (const file of highRiskFiles(changedFiles)) {
    if (coveredFiles.has(file)) continue
    findings.push(highRiskFinding(file))
    if (findings.length >= 120) break
  }
  const requiresManual =
    verdict === 'manual' ||
    highRiskFiles(changedFiles).length > 0 ||
    findings.some(
      (finding) =>
        finding.needsManual ||
        finding.severity === 'blocking' ||
        HIGH_RISK_FILE.test(finding.file),
    )
  return {
    verdict: requiresManual ? 'manual' : 'pass',
    summary:
      summary ||
      (requiresManual
        ? 'Codex 初审发现需要人工确认的内容。'
        : 'Codex 初审未发现需要人工介入的问题。'),
    findings,
  }
}

function highRiskFiles(files: string[]) {
  return [
    ...new Set(
      files
        .map((file) => file.trim())
        .filter((file) => file && HIGH_RISK_FILE.test(file)),
    ),
  ]
}

function highRiskFinding(file: string): CodeReviewFinding {
  return {
    id: `finding-risk-${stableHash(file)}`,
    title: '高风险文件改动需人工确认',
    file,
    line: null,
    severity: 'warning',
    needsManual: true,
    reason:
      '本次评审范围包含认证、权限、安全、数据库迁移或发布配置等高风险文件。即使 Codex 未将其列为具体问题，也需要人工确认改动意图和影响。',
    suggestion:
      '检查该文件的变更范围、调用方和测试覆盖；确认无需修改后，可以将此条标记为误报。',
    status: 'pending',
  }
}

function previewFiles(files: string[]) {
  const normalized = files.filter(Boolean)
  const shown = normalized.slice(0, 80).join('、')
  return normalized.length > 80
    ? `${shown} 等 ${normalized.length} 个文件`
    : shown
}

export function codeReviewRepairPrompt(
  record: CodeReviewRecord,
  findings: CodeReviewFinding[],
) {
  return [
    '【代码评审问题修复】',
    `仓库：${record.repositoryName}`,
    `仓库路径：${record.repositoryPath}`,
    `分支：${record.branchLabel}`,
    `基线：${record.baseBranch}`,
    `评审提交：${record.baseCommit}..${record.headCommit}`,
    '',
    '请只处理本次人工审核确认的问题，不扩展无关需求，不提交代码。',
    '',
    '【需要修复的问题】',
    ...(findings.length > 0
      ? findings.flatMap((finding, index) => [
          `${index + 1}. ${finding.title}`,
          `文件：${finding.file}${finding.line ? `:${finding.line}` : ''}`,
          `原因：${finding.reason || '未提供'}`,
          `建议：${finding.suggestion || '结合代码判断合理修复方式'}`,
          '',
        ])
      : ['未勾选具体问题，请以人工备注为准。']),
    record.manualNote.trim()
      ? `【人工备注】\n${record.manualNote.trim()}`
      : '',
    '',
    '【执行要求】',
    `1. 先检查工作区状态并确认位于评审分支「${record.branchLabel}」。如果当前不在该分支，在不存在未提交改动时切换到对应本地分支；远端分支可建立本地跟踪分支。存在未提交改动或切换冲突时必须停止，不得覆盖现有工作。`,
    '2. 复核问题在当前代码和评审分支中的真实状态，排除已修复或误报。',
    '3. 完成必要代码修改，保持现有架构、风格和最小改动原则。',
    '4. 执行与改动相关的测试、类型检查或构建验证。',
    '5. 不要自动提交。',
    '6. 最后逐项说明问题是否修复、修改位置和验证结果。',
  ]
    .filter(Boolean)
    .join('\n')
}

function normalizeFinding(
  value: unknown,
  index: number,
): CodeReviewFinding | null {
  if (!value || typeof value !== 'object') return null
  const item = value as Record<string, unknown>
  const title = text(item.title).slice(0, 300)
  const file = text(item.file).slice(0, 1000)
  if (!title || !file) return null
  const severity = severityValue(item.severity)
  const lineValue = Number(item.line)
  return {
    id: `finding-${index}-${stableHash(`${file}:${title}`)}`,
    title,
    file,
    line:
      Number.isInteger(lineValue) && lineValue > 0
        ? lineValue
        : null,
    severity,
    needsManual:
      item.needsManual === true ||
      severity === 'blocking' ||
      HIGH_RISK_FILE.test(file),
    reason: text(item.reason).slice(0, 4000),
    suggestion: text(item.suggestion).slice(0, 4000),
    status: 'pending',
  }
}

function severityValue(value: unknown): CodeReviewFindingSeverity {
  return value === 'blocking' || value === 'suggestion'
    ? value
    : 'warning'
}

function text(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function stableHash(value: string) {
  let hash = 2166136261
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return (hash >>> 0).toString(36)
}
