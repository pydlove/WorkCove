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
    loadSequence: 0,
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
      const sequence = ++this.loadSequence
      const workspaceChanged = this.workspaceId !== workspaceId
      this.workspaceId = workspaceId
      this.loading = true
      if (workspaceChanged) {
        this.connections = []
        this.loaded = false
      }
      try {
        const connections = await listDatabaseConnections(workspaceId)
        if (
          sequence !== this.loadSequence ||
          this.workspaceId !== workspaceId
        ) {
          return
        }
        this.connections = connections
        this.loaded = true
      } finally {
        if (
          sequence === this.loadSequence &&
          this.workspaceId === workspaceId
        ) {
          this.loading = false
        }
      }
    },
    async save(input: DatabaseConnectionInput) {
      const workspaceId = this.workspaceId
      if (!workspaceId || input.workspaceId !== workspaceId) {
        throw new Error('数据库连接不属于当前工作空间')
      }
      const saved = input.id
        ? await updateDatabaseConnection(input)
        : await createDatabaseConnection(input)
      if (this.workspaceId !== workspaceId) return saved
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
      const workspaceId = this.workspaceId
      if (!workspaceId) return
      await deleteDatabaseConnection(id, workspaceId)
      if (this.workspaceId !== workspaceId) return
      this.connections = this.connections.filter(
        (connection) => connection.id !== id,
      )
    },
    test(input: DatabaseConnectionInput) {
      return testDatabaseConnection(input)
    },
    execute(id: number, sql: string) {
      return executeDatabaseQuery(id, this.workspaceId, sql)
    },
    schemas(id: number): Promise<DatabaseSchema[]> {
      return listDatabaseSchemas(id, this.workspaceId)
    },
    tables(id: number, database: string): Promise<DatabaseTable[]> {
      return listDatabaseTables(id, this.workspaceId, database)
    },
    columns(
      id: number,
      database: string,
      table: string,
    ): Promise<DatabaseColumn[]> {
      return listDatabaseColumns(
        id,
        this.workspaceId,
        database,
        table,
      )
    },
    tableData(
      id: number,
      database: string,
      table: string,
    ): Promise<DatabaseQueryResult> {
      return loadDatabaseTableData(
        id,
        this.workspaceId,
        database,
        table,
      )
    },
    clear() {
      this.workspaceId = ''
      this.connections = []
      this.loaded = false
      this.loading = false
      this.loadSequence += 1
    },
  },
})
