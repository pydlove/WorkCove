export type ShortcutMode = 'website' | 'application'
export type ShortcutIconMode = 'auto' | 'custom'

interface ShortcutBase {
  id: string
  name: string
  iconMode: ShortcutIconMode
  iconDataUrl: string | null
  createdAt: string
}

export interface WebsiteShortcut extends ShortcutBase {
  url: string
}

export interface ApplicationShortcut extends ShortcutBase {
  path: string
}

export interface WorkbenchShortcuts {
  websites: WebsiteShortcut[]
  applications: ApplicationShortcut[]
}

export interface ShortcutInput {
  id?: string
  mode: ShortcutMode
  name: string
  target: string
  iconMode: ShortcutIconMode
  iconDataUrl: string | null
}

export interface SelectedApplication {
  path: string
  name: string
  iconDataUrl: string | null
}
