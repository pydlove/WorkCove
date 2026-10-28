import type {
  ApplicationShortcut,
  WebsiteShortcut,
  WorkbenchShortcuts,
} from '@/types/shortcut'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'
import {
  readUserScopedMap,
  requireUserScope,
  writeUserScopedMap,
} from '@/services/userScopedStorage'

const SHORTCUT_STORAGE_KEY = 'workcove.workbench.shortcuts.v1'
export const DEFAULT_WEBSITE_CATEGORY = '未分类'
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
    icons: {},
    categoriesByWorkspace: {
      [DEFAULT_WORKSPACE_ID]: [DEFAULT_WEBSITE_CATEGORY],
    },
  }
}

function iconKey(dataUrl: string) {
  let hash = 2166136261
  for (let index = 0; index < dataUrl.length; index++) {
    hash ^= dataUrl.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return `icon-${(hash >>> 0).toString(36)}-${dataUrl.length.toString(36)}`
}

function compactShortcuts(
  shortcuts: WorkbenchShortcuts,
  existingIcons: Record<string, string> = {},
) {
  const icons = { ...existingIcons }
  const usedIconKeys = new Set<string>()

  function compactItem<T extends WebsiteShortcut | ApplicationShortcut>(
    item: T,
  ): T {
    if (item.iconDataUrl) {
      const key = iconKey(item.iconDataUrl)
      icons[key] = item.iconDataUrl
      usedIconKeys.add(key)
      return { ...item, iconDataUrl: null, iconKey: key }
    }
    if (item.iconKey && icons[item.iconKey]) {
      usedIconKeys.add(item.iconKey)
    }
    return item
  }

  const compacted: WorkbenchShortcuts = {
    ...shortcuts,
    websites: shortcuts.websites.map(compactItem),
    applications: shortcuts.applications.map(compactItem),
    icons: {},
  }
  compacted.icons = Object.fromEntries(
    [...usedIconKeys]
      .filter((key) => Boolean(icons[key]))
      .map((key) => [key, icons[key]]),
  )
  return compacted
}

function resolveShortcuts(shortcuts: WorkbenchShortcuts) {
  const icons = shortcuts.icons ?? {}
  function resolveItem<T extends WebsiteShortcut | ApplicationShortcut>(
    item: T,
  ): T {
    return {
      ...item,
      iconDataUrl:
        item.iconDataUrl ??
        (item.iconKey ? icons[item.iconKey] ?? null : null),
    }
  }
  return {
    ...shortcuts,
    websites: shortcuts.websites.map(resolveItem),
    applications: shortcuts.applications.map(resolveItem),
  }
}

function normalizeCategoriesByWorkspace(
  value: unknown,
  websites: WebsiteShortcut[],
  legacyCategories?: string[],
) {
  const source =
    value && typeof value === 'object'
      ? (value as Record<string, unknown>)
      : {}
  const categoriesByWorkspace: Record<string, string[]> = {}
  const workspaceIds = new Set<string>([
    DEFAULT_WORKSPACE_ID,
    ...Object.keys(source),
    ...websites.map((website) => website.workspaceId),
  ])

  for (const workspaceId of workspaceIds) {
    const stored = Array.isArray(source[workspaceId])
      ? (source[workspaceId] as string[])
      : []
    const legacy =
      workspaceId === DEFAULT_WORKSPACE_ID &&
      Array.isArray(legacyCategories)
        ? legacyCategories
        : []
    categoriesByWorkspace[workspaceId] = [
      ...new Set([
        ...stored
          .filter((category) => typeof category === 'string')
          .map((category) => category.trim())
          .filter(Boolean),
        ...legacy
          .filter((category) => typeof category === 'string')
          .map((category) => category.trim())
          .filter(Boolean),
        ...websites
          .filter((website) => website.workspaceId === workspaceId)
          .map((website) => website.category),
        DEFAULT_WEBSITE_CATEGORY,
      ]),
    ]
  }

  return categoriesByWorkspace
}

function normalizeWebsite(website: WebsiteShortcut): WebsiteShortcut {
  const category =
    typeof website.category === 'string' ? website.category.trim() : ''
  const clickCount = Number.isFinite(website.clickCount)
    ? Math.max(0, Math.floor(website.clickCount))
    : 0
  const lastClickedAt =
    typeof website.lastClickedAt === 'string'
      ? website.lastClickedAt
      : null

  return {
    ...website,
    workspaceId:
      typeof website.workspaceId === 'string' && website.workspaceId
        ? website.workspaceId
        : DEFAULT_WORKSPACE_ID,
    category: category || DEFAULT_WEBSITE_CATEGORY,
    clickCount,
    lastClickedAt,
  }
}

function normalizeApplication(
  application: ApplicationShortcut,
): ApplicationShortcut {
  const clickCount = Number.isFinite(application.clickCount)
    ? Math.max(0, Math.floor(application.clickCount))
    : 0
  const lastOpenedAt =
    typeof application.lastOpenedAt === 'string'
      ? application.lastOpenedAt
      : null

  return {
    ...application,
    workspaceId:
      typeof application.workspaceId === 'string' &&
      application.workspaceId
        ? application.workspaceId
        : DEFAULT_WORKSPACE_ID,
    clickCount,
    lastOpenedAt,
  }
}

function readShortcutMap(): Record<string, WorkbenchShortcuts> {
  return readUserScopedMap<WorkbenchShortcuts>(SHORTCUT_STORAGE_KEY)
}

function writeShortcutMap(map: Record<string, WorkbenchShortcuts>) {
  writeUserScopedMap(SHORTCUT_STORAGE_KEY, map)
}

export function loadLocalShortcuts(email: string): WorkbenchShortcuts {
  const normalizedEmail = requireUserScope(email)
  const map = readShortcutMap()
  const existing = map[normalizedEmail]
  if (existing) {
    const websites = Array.isArray(existing.websites)
      ? existing.websites
          .filter(
            (item) =>
              !(
                item.iconMode === 'auto' &&
                item.iconDataUrl === null &&
                LEGACY_DEFAULT_WEBSITES.has(`${item.name}|${item.url}`)
              ),
          )
          .map(normalizeWebsite)
      : []
    const cleaned: WorkbenchShortcuts = {
      websites,
      applications: Array.isArray(existing.applications)
        ? existing.applications.map(normalizeApplication)
        : [],
      icons: existing.icons ?? {},
      categoriesByWorkspace: normalizeCategoriesByWorkspace(
        existing.categoriesByWorkspace,
        websites,
        existing.categories,
      ),
    }
    const compacted = compactShortcuts(cleaned, existing.icons)
    map[normalizedEmail] = compacted
    writeShortcutMap(map)
    return resolveShortcuts(compacted)
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
  const normalizedEmail = requireUserScope(email)
  const map = readShortcutMap()
  const existingIcons = map[normalizedEmail]?.icons ?? {}
  map[normalizedEmail] = compactShortcuts(shortcuts, existingIcons)
  for (const [scope, stored] of Object.entries(map)) {
    map[scope] = compactShortcuts(stored, stored.icons)
  }
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

export function sortWebsitesByUsage(websites: WebsiteShortcut[]) {
  return [...websites].sort(
    (left, right) =>
      right.clickCount - left.clickCount ||
      right.createdAt.localeCompare(left.createdAt),
  )
}

export function sortApplicationsByUsage(
  applications: ApplicationShortcut[],
) {
  return [...applications].sort(
    (left, right) =>
      right.clickCount - left.clickCount ||
      right.createdAt.localeCompare(left.createdAt),
  )
}
