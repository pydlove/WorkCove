import {
  deleteNoContentWithHeaders,
  get,
  postJsonWithHeaders,
  putJsonWithHeaders,
} from '@/api/http'
import type {
  DatabaseConnection,
  DatabaseConnectionInput,
  DatabaseConnectionTest,
  DatabaseColumn,
  DatabaseQueryResult,
  DatabaseSchema,
  DatabaseTable,
} from '@/types/database'

const ACTION_HEADER = { 'X-WorkCove-Action': 'manage-databases' }

function requestBody(input: DatabaseConnectionInput) {
  return {
    id: input.id,
    workspaceId: input.workspaceId,
    name: input.name,
    type: input.type,
    host: input.host,
    port: input.port,
    databaseName: input.databaseName,
    username: input.username,
    password: input.password,
    sslEnabled: input.sslEnabled,
  }
}

export const listDatabaseConnections = (workspaceId: string) =>
  get<DatabaseConnection[]>(
    `/development/databases/connections?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const createDatabaseConnection = (
  input: DatabaseConnectionInput,
) =>
  postJsonWithHeaders<DatabaseConnection>(
    '/development/databases/connections',
    requestBody(input),
    ACTION_HEADER,
  )

export const updateDatabaseConnection = (
  input: DatabaseConnectionInput,
) =>
  putJsonWithHeaders<DatabaseConnection>(
    `/development/databases/connections/${input.id}`,
    requestBody(input),
    ACTION_HEADER,
  )

export const deleteDatabaseConnection = (id: number) =>
  deleteNoContentWithHeaders(
    `/development/databases/connections/${id}`,
    ACTION_HEADER,
  )

export const testDatabaseConnection = (
  input: DatabaseConnectionInput,
) =>
  postJsonWithHeaders<DatabaseConnectionTest>(
    '/development/databases/connections/test',
    requestBody(input),
    ACTION_HEADER,
  )

export const executeDatabaseQuery = (id: number, sql: string) =>
  postJsonWithHeaders<DatabaseQueryResult>(
    `/development/databases/connections/${id}/query`,
    { sql },
    ACTION_HEADER,
    45_000,
  )

export const listDatabaseSchemas = (id: number) =>
  get<DatabaseSchema[]>(
    `/development/databases/connections/${id}/databases`,
  )

export const listDatabaseTables = (id: number, database: string) =>
  get<DatabaseTable[]>(
    `/development/databases/connections/${id}/tables?database=${encodeURIComponent(database)}`,
  )

export const listDatabaseColumns = (
  id: number,
  database: string,
  table: string,
) =>
  get<DatabaseColumn[]>(
    `/development/databases/connections/${id}/columns?database=${encodeURIComponent(database)}&table=${encodeURIComponent(table)}`,
  )

export const loadDatabaseTableData = (
  id: number,
  database: string,
  table: string,
) =>
  get<DatabaseQueryResult>(
    `/development/databases/connections/${id}/table-data?database=${encodeURIComponent(database)}&table=${encodeURIComponent(table)}`,
  )
