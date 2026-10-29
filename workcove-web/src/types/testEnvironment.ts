export interface TestEnvironment {
  id: number
  workspaceId: string
  name: string
  url: string
  username: string
  hasPassword: boolean
  createdAt: string
  updatedAt: string
}

export interface TestEnvironmentInput {
  id?: number
  workspaceId: string
  name: string
  url: string
  username: string
  password: string
}

export interface TestEnvironmentCredentials {
  username: string
  password: string
}
