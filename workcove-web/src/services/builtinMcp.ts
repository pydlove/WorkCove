export type BuiltinMcpId =
  | 'workcove_browser'
  | 'capability_set_update'

export interface BuiltinMcpDefinition {
  id: BuiltinMcpId
  name: string
  description: string
  promptLines: string[]
  defaultPrompt: string
}

export const BUILTIN_MCP_CATALOG: BuiltinMcpDefinition[] = [
  {
    id: 'workcove_browser',
    name: '内置浏览器',
    description: '网页登录、页面操作、截图与下载',
    promptLines: [
      '使用 WorkCove Browser MCP（workcove_browser）操作客户端内置浏览器。',
      '需要登录态时优先复用内置浏览器会话；涉及密码时使用密码库填充，不要让用户粘贴密码。',
      '页面操作前先调用 browser_snapshot，下载后使用 browser_downloads 获取实际路径。',
    ],
    defaultPrompt: '请打开 WorkCove 内置浏览器，等待我继续输入要访问的网址。',
  },
  {
    id: 'capability_set_update',
    name: '能力集更新',
    description: '保存配置并更新 CAPA_CONFIG_AISG.json',
    promptLines: [
      '使用 WorkCove Browser MCP 的 capability_set_update 工具。',
      '能力集网站和账号分别从 WorkCove 网址库、已解锁密码库获取，不要要求用户提供密码。',
      '先使用 confirm=false 预览当前 app_audit 分支与能力级匹配结果；只有用户明确确认后才使用 confirm=true 保存、下载并覆盖文件。',
    ],
    defaultPrompt:
      '请预览当前 app_audit 分支对应的能力集更新。先不要执行 confirm=true，等我确认匹配结果后再更新。',
  },
]

export function selectedBuiltinMcpDefinitions(ids: BuiltinMcpId[]) {
  const selected = new Set(ids)
  return BUILTIN_MCP_CATALOG.filter((definition) =>
    selected.has(definition.id),
  )
}

export function promptWithBuiltinMcpContext(
  prompt: string,
  ids: BuiltinMcpId[],
) {
  const definitions = selectedBuiltinMcpDefinitions(ids)
  if (definitions.length === 0) return prompt
  const task =
    prompt.trim() ||
    definitions.map((definition) => definition.defaultPrompt).join('\n')
  const mcpLines = definitions.flatMap((definition, index) => [
    `${index + 1}. ${definition.name}（${definition.id}）`,
    ...definition.promptLines.map((line) => `- ${line}`),
  ])
  return [
    '【指定内置 MCP】',
    '本任务需要优先使用以下 WorkCove 内置 MCP 能力，不要改用普通网页请求替代。',
    ...mcpLines,
    '',
    '【任务要求】',
    task,
  ].join('\n')
}
