import type {
  ApplicationShortcut,
  WebsiteShortcut,
  WorkbenchShortcuts,
} from '@/types/shortcut'

const SHORTCUT_STORAGE_KEY = 'workcove.workbench.shortcuts.v1'

function createId() {
  return window.crypto.randomUUID()
}

function defaultShortcuts(): WorkbenchShortcuts {
  const createdAt = new Date().toISOString()
  return {
    websites: [
      {
        id: createId(),
        name: 'GitHub',
        url: 'https://github.com',
        iconMode: 'auto',
        iconDataUrl: null,
        createdAt,
      },
      {
        id: createId(),
        name: 'WorkCove Repository',
        url: 'https://github.com/pydlove/WorkCove',
        iconMode: 'auto',
        iconDataUrl: null,
        createdAt,
      },
      {
        id: createId(),
        name: 'MDN Web Docs',
        url: 'https://developer.mozilla.org',
        iconMode: 'auto',
        iconDataUrl: null,
        createdAt,
      },
    ],
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
    return {
      websites: Array.isArray(existing.websites) ? existing.websites : [],
      applications: Array.isArray(existing.applications)
        ? existing.applications
        : [],
    }
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

export function websiteFaviconUrl(url: string) {
  try {
    return `${new URL(url).origin}/favicon.ico`
  } catch {
    return null
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

export function shortcutIconUrl(
  shortcut: WebsiteShortcut | ApplicationShortcut,
) {
  if (shortcut.iconDataUrl) return shortcut.iconDataUrl
  if ('url' in shortcut && shortcut.iconMode === 'auto') {
    return websiteFaviconUrl(shortcut.url)
  }
  return null
}
