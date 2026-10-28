export type ShortcutMode = 'website' | 'application'
export type ShortcutIconMode = 'auto' | 'custom'

interface ShortcutBase {
  id: string
  workspaceId: string
  name: string
  iconMode: ShortcutIconMode
  iconDataUrl: string | null
  iconKey?: string
  createdAt: string
}

export interface WebsiteShortcut extends ShortcutBase {
  url: string
  category: string
  clickCount: number
  lastClickedAt: string | null
}

export interface ApplicationShortcut extends ShortcutBase {
  path: string
  clickCount: number
  lastOpenedAt: string | null
}

export interface WorkbenchShortcuts {
  websites: WebsiteShortcut[]
  applications: ApplicationShortcut[]
  categoriesByWorkspace: Record<string, string[]>
  categories?: string[]
  icons?: Record<string, string>
}

export interface ShortcutInput {
  id?: string
  workspaceId?: string
  mode: ShortcutMode
  name: string
  target: string
  iconMode: ShortcutIconMode
  iconDataUrl: string | null
  category?: string
}

export interface SelectedApplication {
  path: string
  name: string
  iconDataUrl: string | null
}
