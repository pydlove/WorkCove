export type DatabaseType = 'MYSQL' | 'CLICKHOUSE'

export interface DatabaseConnection {
  id: number
  workspaceId: string
  name: string
  type: DatabaseType
  host: string
  port: number
  databaseName: string
  username: string
  sslEnabled: boolean
  createdAt: string
  updatedAt: string
}

export interface DatabaseConnectionInput {
  id?: number
  workspaceId: string
  name: string
  type: DatabaseType
  host: string
  port: number
  databaseName: string
  username: string
  password: string
  sslEnabled: boolean
}

export interface DatabaseConnectionTest {
  success: boolean
  message: string
  elapsedMs: number
}

export interface DatabaseQueryColumn {
  name: string
  type: string
}

export interface DatabaseQueryResult {
  resultSet: boolean
  columns: DatabaseQueryColumn[]
  rows: Array<Array<string | null>>
  affectedRows: number
  truncated: boolean
  elapsedMs: number
}

export interface DatabaseTable {
  name: string
  type: string
}

export interface DatabaseSchema {
  name: string
}

export interface DatabaseColumn {
  name: string
  type: string
  nullable: boolean
  key: string
  defaultValue: string
  extra: string
  comment: string
}
