function resourceError(message, statusCode = 409) {
  const error = new Error(message)
  error.statusCode = statusCode
  return error
}

function normalizedName(value) {
  return typeof value === 'string'
    ? value.trim().toLocaleLowerCase('en-US')
    : ''
}

function websiteOrigin(value) {
  try {
    const parsed = new URL(value)
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return ''
    }
    return parsed.origin
  } catch {
    return ''
  }
}

function selectWebsite(websites, preferredName = '') {
  const list = Array.isArray(websites) ? websites : []
  const preferred = normalizedName(preferredName)
  if (preferred) {
    const exact = list.filter(
      (website) => normalizedName(website?.name) === preferred,
    )
    if (exact.length === 1) return exact[0]
    if (exact.length > 1) {
      throw resourceError('网址库中存在多个同名能力集网站')
    }
  }
  const keywords = preferred
    ? [preferred]
    : ['产品能力集管理系统', '产品能力集', '能力集']
  const matching = list.filter((website) => {
    const name = normalizedName(website?.name)
    const url = String(website?.url || '').toLocaleLowerCase('en-US')
    return (
      keywords.some((keyword) => name.includes(keyword)) ||
      url.includes('productcapabilitylist')
    )
  })
  if (matching.length === 1) return matching[0]
  if (matching.length > 1) {
    throw resourceError(
      `网址库中匹配到多个能力集网站：${matching
        .map((website) => website.name)
        .join('、')}`,
    )
  }
  throw resourceError(
    preferredName
      ? `网址库中没有找到「${preferredName}」`
      : '网址库中没有找到产品能力集管理系统',
    404,
  )
}

function selectCredential(entries, website, preferredName = '') {
  const list = Array.isArray(entries) ? entries : []
  const preferred = normalizedName(preferredName)
  if (preferred) {
    const exact = list.filter(
      (entry) => normalizedName(entry?.name) === preferred,
    )
    if (exact.length === 1) return exact[0]
    if (exact.length > 1) {
      throw resourceError('密码库中存在多个同名能力集账号')
    }
    throw resourceError(`密码库中没有找到「${preferredName}」`, 404)
  }

  const origin = websiteOrigin(website?.url)
  const originMatches = origin
    ? list.filter((entry) => websiteOrigin(entry?.url) === origin)
    : []
  if (originMatches.length === 1) return originMatches[0]
  if (originMatches.length > 1) {
    throw resourceError(
      `密码库中同一网址有多个账号，请指定 credentialName：${originMatches
        .map((entry) => entry.name)
        .join('、')}`,
    )
  }

  const nameMatches = list.filter((entry) =>
    normalizedName(entry?.name).includes('能力集'),
  )
  if (nameMatches.length === 1) return nameMatches[0]
  if (nameMatches.length > 1) {
    throw resourceError(
      `密码库中匹配到多个能力集账号，请指定 credentialName：${nameMatches
        .map((entry) => entry.name)
        .join('、')}`,
    )
  }
  throw resourceError(
    '密码库中没有找到与该能力集网站匹配的账号，请补充条目网址或指定 credentialName',
    404,
  )
}

async function resolveCapabilityResources({
  apiRequest,
  websiteName = '',
  credentialName = '',
}) {
  if (typeof apiRequest !== 'function') {
    throw new TypeError('apiRequest 必须是函数')
  }
  const user = await apiRequest('/auth/me')
  const email = normalizedName(user?.email)
  if (!email) {
    throw resourceError('WorkCove 登录账号无效，请重新登录', 401)
  }

  const state = await apiRequest('/development/user-state')
  const rawShortcuts = state?.['workcove.workbench.shortcuts.v1']
  let shortcuts
  try {
    shortcuts =
      typeof rawShortcuts === 'string'
        ? JSON.parse(rawShortcuts)
        : rawShortcuts
  } catch {
    throw resourceError('网址库数据格式无效')
  }
  const scopedShortcuts = shortcuts?.[email]
  const websites = scopedShortcuts?.websites
  const website = selectWebsite(websites, websiteName)
  const workspaceId =
    typeof website.workspaceId === 'string'
      ? website.workspaceId.trim()
      : ''
  if (!workspaceId) {
    throw resourceError('能力集网站缺少工作空间信息')
  }

  const query = new URLSearchParams({ workspaceId })
  const entries = await apiRequest(`/vault/entries?${query}`)
  const credential = selectCredential(
    entries,
    website,
    credentialName,
  )
  const secret = await apiRequest(
    `/vault/entries/${encodeURIComponent(credential.id)}/secret?${query}`,
  )
  const username =
    typeof credential.username === 'string'
      ? credential.username.trim()
      : ''
  const password =
    typeof secret?.password === 'string' ? secret.password : ''
  if (!username || !password) {
    throw resourceError('密码库中的能力集账号或密码为空')
  }
  return {
    email: user.email,
    website: {
      id: website.id,
      name: website.name,
      url: website.url,
      workspaceId,
    },
    credential: {
      id: credential.id,
      name: credential.name,
      username,
    },
    password,
  }
}

module.exports = {
  resolveCapabilityResources,
  selectCredential,
  selectWebsite,
}
