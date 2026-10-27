import type {
  ApplicationShortcut,
  WebsiteShortcut,
  WorkbenchShortcuts,
} from '@/types/shortcut'

const SHORTCUT_STORAGE_KEY = 'workcove.workbench.shortcuts.v1'
const LEGACY_DEFAULT_WEBSITES = new Set([
  'GitHub|https://github.com',
  'WorkCove Repository|https://github.com/pydlove/WorkCove',
  'MDN Web Docs|https://developer.mozilla.org',
])

function createId() {
  return window.crypto.randomUUID()
}

function defaultShortcuts(): WorkbenchShortcuts {
  return {
    websites: [],
    applications: [],
  }
}

function readShortcutMap(): Record<string, WorkbenchShortcuts> {
  const raw = window.localStorage.getItem(SHORTCUT_STORAGE_KEY)
  if (!raw) return {}

  try {
    const parsed = JSON.parse(raw) as Record<string, WorkbenchShortcuts>
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

function writeShortcutMap(map: Record<string, WorkbenchShortcuts>) {
  window.localStorage.setItem(SHORTCUT_STORAGE_KEY, JSON.stringify(map))
}

export function loadLocalShortcuts(email: string): WorkbenchShortcuts {
  const normalizedEmail =
    email.trim().toLocaleLowerCase('en-US') || 'preview@workcove.local'
  const map = readShortcutMap()
  const existing = map[normalizedEmail]
  if (existing) {
    const websites = Array.isArray(existing.websites)
      ? existing.websites.filter(
          (item) =>
            !(
              item.iconMode === 'auto' &&
              item.iconDataUrl === null &&
              LEGACY_DEFAULT_WEBSITES.has(`${item.name}|${item.url}`)
            ),
        )
      : []
    const cleaned: WorkbenchShortcuts = {
      websites,
      applications: Array.isArray(existing.applications)
        ? existing.applications
        : [],
    }
    if (cleaned.websites.length !== existing.websites?.length) {
      map[normalizedEmail] = cleaned
      writeShortcutMap(map)
    }
    return cleaned
  }

  const defaults = defaultShortcuts()
  map[normalizedEmail] = defaults
  writeShortcutMap(map)
  return defaults
}

export function saveLocalShortcuts(
  email: string,
  shortcuts: WorkbenchShortcuts,
) {
  const normalizedEmail =
    email.trim().toLocaleLowerCase('en-US') || 'preview@workcove.local'
  const map = readShortcutMap()
  map[normalizedEmail] = shortcuts
  writeShortcutMap(map)
}

export function newShortcutId() {
  return createId()
}

export function normalizeWebsiteUrl(rawUrl: string) {
  const value = rawUrl.trim()
  if (!value) throw new Error('请输入网址')

  const candidate = /^[a-z][a-z\d+.-]*:\/\//i.test(value)
    ? value
    : `https://${value}`
  const parsed = new URL(candidate)
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('网址必须使用 http 或 https')
  }
  return parsed.toString()
}

export function websiteFaviconUrls(url: string) {
  try {
    const parsed = new URL(url)
    const host = encodeURIComponent(parsed.hostname)
    return [
      `${parsed.origin}/favicon.ico`,
      `https://favicon.im/${host}?larger=true`,
      `https://www.google.com/s2/favicons?domain=${host}&sz=64`,
    ]
  } catch {
    return []
  }
}

export function websiteHostname(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

export function applicationDisplayName(applicationPath: string) {
  const normalized = applicationPath.replace(/[\\/]+$/, '')
  const name = normalized.split(/[\\/]/).pop() ?? normalized
  return name.replace(/\.(app|exe)$/i, '')
}

export function shortcutIconUrls(
  shortcut: WebsiteShortcut | ApplicationShortcut,
) {
  if (shortcut.iconDataUrl) return [shortcut.iconDataUrl]
  if ('url' in shortcut && shortcut.iconMode === 'auto') {
    return websiteFaviconUrls(shortcut.url)
  }
  return []
}
