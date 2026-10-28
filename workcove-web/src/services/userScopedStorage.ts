import { getUserState, saveUserState } from '@/api/userState'
import { ApiError } from '@/api/http'

const state = new Map<string, unknown>()
const pendingTimers = new Map<string, number>()
const dirtyKeys = new Set<string>()
const writeChains = new Map<string, Promise<void>>()

let activeScope = ''
let persistenceEnabled = false
let hydration: Promise<void> | null = null

export function requireUserScope(scope: string) {
  const normalized = scope.trim().toLocaleLowerCase('en-US')
  if (!normalized) {
    throw new Error('用户未登录，禁止读写用户数据')
  }
  return normalized
}

export async function initializeUserStorage(
  scope: string,
  options: { persist?: boolean } = {},
) {
  const normalized = requireUserScope(scope)
  const persist = options.persist !== false
  if (
    activeScope === normalized &&
    persistenceEnabled === persist &&
    hydration
  ) {
    await hydration
    return
  }

  if (activeScope) {
    await flushUserStorage()
  }

  activeScope = normalized
  persistenceEnabled = persist
  state.clear()
  dirtyKeys.clear()
  hydration = (async () => {
    if (!persist) return
    const stored = await getUserState()
    for (const [key, payload] of Object.entries(stored)) {
      try {
        state.set(key, JSON.parse(payload))
      } catch {
        // Ignore malformed rows instead of blocking the whole workspace.
      }
    }
    await migrateLegacyStorage(normalized)
  })()
  try {
    await hydration
  } catch (error) {
    hydration = null
    if (activeScope === normalized) {
      activeScope = ''
      persistenceEnabled = false
      state.clear()
      dirtyKeys.clear()
    }
    throw error
  }
}

export async function flushUserStorage() {
  const failures: unknown[] = []
  for (const [key, timer] of pendingTimers) {
    window.clearTimeout(timer)
    pendingTimers.delete(key)
    dirtyKeys.add(key)
  }

  const keys = new Set([
    ...dirtyKeys,
    ...writeChains.keys(),
  ])
  for (const key of keys) {
    try {
      await persistKey(key)
    } catch (error) {
      failures.push(error)
    }
  }

  if (failures.length > 0) throw failures[0]
}

export function clearUserStorageCache() {
  activeScope = ''
  persistenceEnabled = false
  hydration = null
  state.clear()
  for (const timer of pendingTimers.values()) window.clearTimeout(timer)
  pendingTimers.clear()
  dirtyKeys.clear()
  writeChains.clear()
}

export function readUserScopedMap<T>(storageKey: string) {
  if (!activeScope || !state.has(storageKey)) {
    return {} as Record<string, T>
  }
  return {
    [activeScope]: state.get(storageKey),
  } as Record<string, T>
}

export function writeUserScopedMap<T>(
  storageKey: string,
  map: Record<string, T>,
) {
  if (!activeScope) {
    throw new Error('用户数据尚未初始化')
  }
  if (!(activeScope in map)) {
    throw new Error('用户数据作用域不匹配')
  }
  state.set(storageKey, map[activeScope])
  schedulePersistence(storageKey)
}

export function readUserScopedValue<T>(
  storageKey: string,
  scope: string,
): T | null {
  const map = readUserScopedMap<T>(storageKey)
  return map[requireUserScope(scope)] ?? null
}

export function writeUserScopedValue<T>(
  storageKey: string,
  scope: string,
  value: T,
) {
  writeUserScopedMap(storageKey, {
    [requireUserScope(scope)]: value,
  })
}

function schedulePersistence(storageKey: string) {
  if (!persistenceEnabled) return
  dirtyKeys.add(storageKey)
  const existing = pendingTimers.get(storageKey)
  if (existing) window.clearTimeout(existing)
  pendingTimers.set(
    storageKey,
    window.setTimeout(() => {
      pendingTimers.delete(storageKey)
      void persistKey(storageKey).catch(() => undefined)
    }, 350),
  )
}

function persistKey(storageKey: string) {
  const previous = writeChains.get(storageKey) ?? Promise.resolve()
  const write = previous
    .catch(() => undefined)
    .then(() => drainKey(storageKey))
  writeChains.set(storageKey, write)
  const cleanUp = () => {
    if (writeChains.get(storageKey) === write) {
      writeChains.delete(storageKey)
    }
  }
  void write.then(cleanUp, cleanUp)
  return write
}

async function drainKey(storageKey: string) {
  if (!persistenceEnabled || !activeScope || !state.has(storageKey)) return

  while (dirtyKeys.delete(storageKey)) {
    const serialized = JSON.stringify(state.get(storageKey))
    const payload = serialized === undefined
      ? null
      : (JSON.parse(serialized) as unknown)
    try {
      await saveUserStateWithRetry(storageKey, payload)
    } catch (error) {
      dirtyKeys.add(storageKey)
      window.dispatchEvent(
        new CustomEvent('workcove:storage-error', {
          detail: {
            key: storageKey,
            message:
              error instanceof Error
                ? error.message
                : '保存到 MySQL 失败',
          },
        }),
      )
      throw error
    }
  }
}

async function saveUserStateWithRetry(
  storageKey: string,
  payload: unknown,
) {
  let lastError: unknown
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      await saveUserState(storageKey, payload)
      return
    } catch (error) {
      lastError = error
      if (
        error instanceof ApiError &&
        (error.status === 401 || error.status === 403)
      ) {
        break
      }
      if (attempt < 3) {
        await new Promise((resolve) => {
          window.setTimeout(resolve, attempt * 300)
        })
      }
    }
  }
  throw lastError
}

async function migrateLegacyStorage(normalizedScope: string) {
  if (!persistenceEnabled) return
  let keys: string[]
  try {
    keys = Array.from(
      { length: window.localStorage.length },
      (_, index) => window.localStorage.key(index),
    ).filter((key): key is string => Boolean(key?.startsWith('workcove.')))
  } catch {
    return
  }

  const legacyThemeKey = 'workcove.theme'
  const themeKey = 'workcove.user-themes.v1'
  if (!state.has(themeKey)) {
    try {
      const legacyTheme = window.localStorage.getItem(legacyThemeKey)
      if (
        legacyTheme === 'light' ||
        legacyTheme === 'dark' ||
        legacyTheme === 'system'
      ) {
        state.set(themeKey, legacyTheme)
        dirtyKeys.add(themeKey)
        await persistKey(themeKey)
      }
    } catch {
      // Blocked browser storage must not prevent MySQL hydration.
    }
  }

  for (const key of keys) {
    if (key === legacyThemeKey) continue
    if (state.has(key)) continue
    let raw: string | null
    try {
      raw = window.localStorage.getItem(key)
    } catch {
      continue
    }
    if (!raw) continue
    let parsed: unknown
    try {
      parsed = JSON.parse(raw)
    } catch {
      continue
    }
    if (!parsed || typeof parsed !== 'object') continue
    const map = parsed as Record<string, unknown>
    if (!(normalizedScope in map)) continue

    state.set(key, map[normalizedScope])
    dirtyKeys.add(key)
    await persistKey(key)

    const remaining = { ...map }
    delete remaining[normalizedScope]
    try {
      if (Object.keys(remaining).length === 0) {
        window.localStorage.removeItem(key)
      } else {
        window.localStorage.setItem(key, JSON.stringify(remaining))
      }
    } catch {
      // A successful MySQL write must not fail because stale browser data
      // cannot be cleaned up.
    }
  }

  for (const staleKey of [
    legacyThemeKey,
    'workcove.last-email',
    'workcove.preview-session',
  ]) {
    try {
      window.localStorage.removeItem(staleKey)
    } catch {
      // Stale preview/login values no longer affect application behavior.
    }
  }
}
