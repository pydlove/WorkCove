import { defineStore } from 'pinia'

import {
  createDatabaseConnection,
  deleteDatabaseConnection,
  executeDatabaseQuery,
  listDatabaseConnections,
  listDatabaseColumns,
  listDatabaseSchemas,
  listDatabaseTables,
  loadDatabaseTableData,
  testDatabaseConnection,
  updateDatabaseConnection,
} from '@/api/databases'
import type {
  DatabaseColumn,
  DatabaseConnection,
  DatabaseConnectionInput,
  DatabaseQueryResult,
  DatabaseSchema,
  DatabaseTable,
} from '@/types/database'

export const useDatabaseStore = defineStore('databases', {
  state: () => ({
    workspaceId: '',
    connections: [] as DatabaseConnection[],
    loading: false,
    loaded: false,
  }),
  actions: {
    async load(workspaceId: string, force = false) {
      if (
        !force &&
        this.loaded &&
        this.workspaceId === workspaceId
      ) {
        return
      }
      this.workspaceId = workspaceId
      this.loading = true
      try {
        this.connections = await listDatabaseConnections(workspaceId)
        this.loaded = true
      } finally {
        this.loading = false
      }
    },
    async save(input: DatabaseConnectionInput) {
      const saved = input.id
        ? await updateDatabaseConnection(input)
        : await createDatabaseConnection(input)
      const existing = this.connections.some(
        (connection) => connection.id === saved.id,
      )
      this.connections = existing
        ? this.connections.map((connection) =>
            connection.id === saved.id ? saved : connection,
          )
        : [saved, ...this.connections]
      return saved
    },
    async remove(id: number) {
      await deleteDatabaseConnection(id)
      this.connections = this.connections.filter(
        (connection) => connection.id !== id,
      )
    },
    test(input: DatabaseConnectionInput) {
      return testDatabaseConnection(input)
    },
    execute(id: number, sql: string) {
      return executeDatabaseQuery(id, sql)
    },
    schemas(id: number): Promise<DatabaseSchema[]> {
      return listDatabaseSchemas(id)
    },
    tables(id: number, database: string): Promise<DatabaseTable[]> {
      return listDatabaseTables(id, database)
    },
    columns(
      id: number,
      database: string,
      table: string,
    ): Promise<DatabaseColumn[]> {
      return listDatabaseColumns(id, database, table)
    },
    tableData(
      id: number,
      database: string,
      table: string,
    ): Promise<DatabaseQueryResult> {
      return loadDatabaseTableData(id, database, table)
    },
    clear() {
      this.workspaceId = ''
      this.connections = []
      this.loaded = false
      this.loading = false
    },
  },
})
