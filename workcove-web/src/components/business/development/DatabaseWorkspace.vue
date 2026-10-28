<script setup lang="ts">
import {
  Check,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Clock3,
  Copy,
  Database,
  LoaderCircle,
  Pencil,
  Play,
  Plus,
  Rows3,
  Search,
  Table2,
  Trash2,
  RefreshCw,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import DatabaseConnectionDialog from '@/components/business/development/DatabaseConnectionDialog.vue'
import clickhouseIcon from '@/assets/integrations/clickhouse.svg'
import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import mysqlIcon from '@/assets/integrations/mysql.svg'
import { usePagination } from '@/composables/usePagination'
import { useDatabaseStore } from '@/stores/useDatabaseStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  DatabaseColumn,
  DatabaseConnection,
  DatabaseQueryResult,
  DatabaseSchema,
  DatabaseTable,
} from '@/types/database'

const emit = defineEmits<{
  notify: [message: string]
}>()

const databases = useDatabaseStore()
const workspaces = useWorkspaceStore()
const connectionsCollapsed = ref(false)
const copiedIdentifier = ref('')
const searchQuery = ref('')
const selectedId = ref<number | null>(null)
const dialogOpen = ref(false)
const editingConnection = ref<DatabaseConnection | null>(null)
const deleteTarget = ref<DatabaseConnection | null>(null)
const deleting = ref(false)
const loadError = ref('')
const sql = ref('SELECT 1')
const executing = ref(false)
const queryError = ref('')
const queryResult = ref<DatabaseQueryResult | null>(null)
const confirmExecuteOpen = ref(false)
const queryVersion = ref(0)
const tables = ref<DatabaseTable[]>([])
const databaseSchemas = ref<DatabaseSchema[]>([])
const schemasLoading = ref(false)
const schemasError = ref('')
const expandedDatabaseName = ref('')
const tableSearchQuery = ref('')
const tablesLoading = ref(false)
const tablesError = ref('')
const selectedTable = ref<DatabaseTable | null>(null)
const expandedTableName = ref('')
const columns = ref<DatabaseColumn[]>([])
const columnsLoading = ref(false)
const columnsError = ref('')
const tableDataLoading = ref(false)
const resultContext = ref('')
let tableLoadGeneration = 0
let schemaLoadGeneration = 0
let copyFeedbackTimer: number | null = null

const filteredConnections = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return databases.connections
  return databases.connections.filter((connection) =>
    `${connection.name} ${connection.host} ${connection.port} ${connection.databaseName}`
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})
const {
  currentPage: connectionPage,
  pageItems: pagedConnections,
  setPage: setConnectionPage,
} = usePagination(filteredConnections, () => searchQuery.value)
const selectedConnection = computed(
  () =>
    databases.connections.find(
      (connection) => connection.id === selectedId.value,
    ) ?? null,
)
const queryRows = computed(() => queryResult.value?.rows ?? [])
const {
  currentPage: resultPage,
  pageItems: pagedResultRows,
  setPage: setResultPage,
} = usePagination(queryRows, () => queryVersion.value)
const filteredTables = computed(() => {
  const query = tableSearchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return tables.value
  return tables.value.filter((table) =>
    `${table.name} ${table.type}`.toLocaleLowerCase('zh-CN').includes(query),
  )
})
const {
  currentPage: schemaPage,
  pageItems: pagedSchemas,
  setPage: setSchemaPage,
} = usePagination(databaseSchemas, () => selectedId.value ?? '')
const {
  currentPage: tablePage,
  pageItems: pagedTables,
  setPage: setTablePage,
} = usePagination(filteredTables, () => tableSearchQuery.value)
const {
  currentPage: columnPage,
  pageItems: pagedColumns,
  setPage: setColumnPage,
} = usePagination(columns, () => selectedTable.value?.name ?? '')
const dangerousQuery = computed(
  () =>
    Boolean(sql.value.trim()) &&
    !/^\s*(select|show|describe|desc|explain)\b/i.test(sql.value),
)

watch(
  () => workspaces.activeWorkspaceId,
  (workspaceId) => {
    selectedId.value = null
    void loadConnections(workspaceId, true)
  },
)

onMounted(() => {
  void loadConnections(workspaces.activeWorkspaceId, true)
})

onBeforeUnmount(() => {
  if (copyFeedbackTimer !== null) {
    window.clearTimeout(copyFeedbackTimer)
  }
})

async function loadConnections(workspaceId: string, force = false) {
  loadError.value = ''
  try {
    await databases.load(workspaceId, force)
    if (
      selectedId.value !== null &&
      !databases.connections.some(
        (connection) => connection.id === selectedId.value,
      )
    ) {
      selectedId.value = null
    }
    if (selectedId.value === null && databases.connections[0]) {
      void selectConnection(databases.connections[0])
    }
  } catch (error) {
    loadError.value = errorMessage(error)
  }
}

function errorMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : '数据库操作失败，请稍后重试'
}

async function selectConnection(connection: DatabaseConnection) {
  selectedId.value = connection.id
  sql.value = defaultSql(connection)
  queryResult.value = null
  queryError.value = ''
  queryVersion.value += 1
  selectedTable.value = null
  expandedTableName.value = ''
  columns.value = []
  columnsError.value = ''
  columnsLoading.value = false
  tableDataLoading.value = false
  resultContext.value = ''
  await loadSchemas(connection)
}

function defaultSql(connection: DatabaseConnection) {
  return connection.type === 'MYSQL'
    ? 'SELECT 1;'
    : 'SELECT 1'
}

function addConnection() {
  editingConnection.value = null
  dialogOpen.value = true
}

function editConnection(connection: DatabaseConnection) {
  editingConnection.value = connection
  dialogOpen.value = true
}

function connectionSaved(connection: DatabaseConnection) {
  dialogOpen.value = false
  editingConnection.value = null
  void selectConnection(connection)
}

function requestDelete(connection: DatabaseConnection) {
  deleteTarget.value = connection
}

async function confirmDelete() {
  const connection = deleteTarget.value
  if (!connection || deleting.value) return
  deleting.value = true
  try {
    await databases.remove(connection.id)
    deleteTarget.value = null
    if (selectedId.value === connection.id) {
      selectedId.value = null
      queryResult.value = null
      queryError.value = ''
      if (databases.connections[0]) {
        void selectConnection(databases.connections[0])
      }
    }
    emit('notify', '数据库连接已删除')
  } catch (error) {
    emit('notify', errorMessage(error))
  } finally {
    deleting.value = false
  }
}

function executeQuery() {
  if (!selectedConnection.value || !sql.value.trim() || executing.value) {
    return
  }
  if (dangerousQuery.value) {
    confirmExecuteOpen.value = true
    return
  }
  void performExecute()
}

async function performExecute() {
  const connection = selectedConnection.value
  if (!connection || executing.value) return
  confirmExecuteOpen.value = false
  executing.value = true
  queryError.value = ''
  try {
    selectedTable.value = null
    expandedTableName.value = ''
    expandedDatabaseName.value = ''
    columns.value = []
    resultContext.value = ''
    queryResult.value = await databases.execute(connection.id, sql.value)
    queryVersion.value += 1
  } catch (error) {
    queryResult.value = null
    queryError.value = errorMessage(error)
  } finally {
    executing.value = false
  }
}

async function loadSchemas(connection = selectedConnection.value) {
  if (!connection) return
  const generation = ++schemaLoadGeneration
  schemasLoading.value = true
  schemasError.value = ''
  databaseSchemas.value = []
  expandedDatabaseName.value = ''
  tables.value = []
  try {
    const result = await databases.schemas(connection.id)
    if (generation !== schemaLoadGeneration) return
    databaseSchemas.value = result
    const preferred =
      result.find(
        (schema) =>
          schema.name.toLocaleLowerCase('en-US') ===
          connection.databaseName.toLocaleLowerCase('en-US'),
      )?.name ??
      result.find(
        (schema) =>
          ![
            'information_schema',
            'mysql',
            'performance_schema',
            'sys',
            'system',
          ].includes(schema.name.toLocaleLowerCase('en-US')),
      )?.name ??
      result[0]?.name ??
      ''
    if (preferred) await selectDatabase(preferred)
  } catch (error) {
    if (generation !== schemaLoadGeneration) return
    schemasError.value = errorMessage(error)
  } finally {
    if (generation === schemaLoadGeneration) schemasLoading.value = false
  }
}

async function loadTables(
  connection = selectedConnection.value,
  database = expandedDatabaseName.value,
) {
  if (!connection) return
  if (!database) {
    tables.value = []
    return
  }
  const generation = ++tableLoadGeneration
  tablesLoading.value = true
  tablesError.value = ''
  tables.value = []
  try {
    const result = await databases.tables(connection.id, database)
    if (generation !== tableLoadGeneration) return
    tables.value = result
  } catch (error) {
    if (generation !== tableLoadGeneration) return
    tablesError.value = errorMessage(error)
  } finally {
    if (generation === tableLoadGeneration) tablesLoading.value = false
  }
}

async function selectDatabase(database: string) {
  const connection = selectedConnection.value
  if (!connection) return
  expandedDatabaseName.value = database
  selectedTable.value = null
  expandedTableName.value = ''
  columns.value = []
  columnsError.value = ''
  queryResult.value = null
  queryError.value = ''
  resultContext.value = ''
  queryVersion.value += 1
  tableSearchQuery.value = ''
  await loadTables(connection, database)
}

function toggleDatabase(database: string) {
  if (expandedDatabaseName.value === database) {
    expandedDatabaseName.value = ''
    tables.value = []
    selectedTable.value = null
    expandedTableName.value = ''
    columns.value = []
    return
  }
  void selectDatabase(database)
}

async function selectTable(table: DatabaseTable) {
  const connection = selectedConnection.value
  if (!connection) return
  const database = expandedDatabaseName.value
  selectedTable.value = table
  expandedTableName.value = table.name
  columns.value = []
  columnsError.value = ''
  columnsLoading.value = true
  tableDataLoading.value = true
  queryError.value = ''
  queryResult.value = null
  resultContext.value = `表数据 · ${database}.${table.name}`
  sql.value =
    'SELECT * FROM ' +
    (database
      ? `${quoteIdentifier(database)}.${quoteIdentifier(table.name)}`
      : quoteIdentifier(table.name)) +
    ' LIMIT 100;'
  const generation = tableLoadGeneration
  const [columnResult, dataResult] = await Promise.allSettled([
    databases.columns(
      connection.id,
      database,
      table.name,
    ),
    databases.tableData(
      connection.id,
      database,
      table.name,
    ),
  ])
  if (
    generation !== tableLoadGeneration ||
    selectedTable.value?.name !== table.name
  ) {
    return
  }
  if (columnResult.status === 'fulfilled') {
    columns.value = columnResult.value
  } else {
    columnsError.value = errorMessage(columnResult.reason)
  }
  if (dataResult.status === 'fulfilled') {
    queryResult.value = dataResult.value
    queryVersion.value += 1
  } else {
    queryError.value = errorMessage(dataResult.reason)
  }
  columnsLoading.value = false
  tableDataLoading.value = false
}

function toggleTable(table: DatabaseTable) {
  if (expandedTableName.value === table.name) {
    expandedTableName.value = ''
    return
  }
  void selectTable(table)
}

function quoteIdentifier(value: string) {
  return `\`${value.replace(/`/g, '``')}\``
}

function handleEditorKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
    event.preventDefault()
    executeQuery()
  }
}

function displayCell(value: string | null) {
  if (value === null) return 'NULL'
  return value === '' ? '—' : value
}

function typeLabel(connection: DatabaseConnection) {
  return connection.type === 'MYSQL' ? 'MySQL' : 'ClickHouse'
}

function connectionTarget(connection: DatabaseConnection) {
  return `${connection.host}:${connection.port}${
    connection.databaseName ? `/${connection.databaseName}` : ''
  }`
}

function updateSql(value: string) {
  sql.value = value
}

async function copyIdentifier(
  value: string,
  key: string,
  label: string,
) {
  if (!navigator.clipboard?.writeText) {
    emit('notify', '当前环境无法访问剪贴板')
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    copiedIdentifier.value = key
    if (copyFeedbackTimer !== null) {
      window.clearTimeout(copyFeedbackTimer)
    }
    copyFeedbackTimer = window.setTimeout(() => {
      copiedIdentifier.value = ''
    }, 1500)
    emit('notify', `已复制${label}：${value}`)
  } catch {
    emit('notify', `复制${label}失败`)
  }
}
</script>

<template>
  <section class="database-workspace">
    <header class="database-toolbar">
      <span class="database-toolbar__count">
        {{ filteredConnections.length }} 个数据库连接
      </span>
      <label class="database-search">
        <Search :size="15" aria-hidden="true" />
        <input
          v-model="searchQuery"
          type="search"
          aria-label="搜索数据库连接"
          placeholder="搜索名称、主机或数据库"
        />
      </label>
      <UiButton variant="primary" @click="addConnection">
        <Plus :size="15" />
        添加连接
      </UiButton>
    </header>

    <div v-if="loadError" class="database-load-error" role="alert">
      <CircleAlert :size="15" />
      <span>{{ loadError }}</span>
      <button
        type="button"
        @click="loadConnections(workspaces.activeWorkspaceId, true)"
      >
        重试
      </button>
    </div>

    <div
      class="database-layout"
      :class="{
        'database-layout--collapsed': connectionsCollapsed,
      }"
    >
      <aside class="database-connections" aria-label="数据库连接列表">
        <template v-if="!connectionsCollapsed">
        <header class="database-connections__header">
          <span>连接</span>
          <button
            type="button"
            aria-label="收起数据库连接配置"
            title="收起连接配置"
            @click="connectionsCollapsed = true"
          >
            <ChevronLeft :size="13" />
          </button>
        </header>
        <div
          v-if="databases.loading && databases.connections.length === 0"
          class="database-connections__state"
        >
          <LoaderCircle class="database-spin" :size="16" />
          正在读取连接…
        </div>

        <template v-else-if="pagedConnections.length > 0">
          <div
            v-for="connection in pagedConnections"
            :key="connection.id"
            class="database-connection-item"
            :class="[
              `database-connection-item--${connection.type.toLocaleLowerCase()}`,
              {
                'database-connection-item--active':
                  connection.id === selectedId,
              },
            ]"
          >
            <button
              class="database-connection-item__main"
              type="button"
              @click="selectConnection(connection)"
            >
              <span
                class="database-connection-item__icon"
                :class="`database-connection-item__icon--${connection.type.toLocaleLowerCase()}`"
              >
                <img
                  v-if="connection.type === 'MYSQL'"
                  :src="mysqlIcon"
                  alt=""
                />
                <img
                  v-else
                  :src="clickhouseIcon"
                  alt=""
                />
              </span>
              <span class="database-connection-item__copy">
                <strong>{{ connection.name }}</strong>
                <small>{{ connectionTarget(connection) }}</small>
              </span>
              <span class="database-connection-item__type">
                {{ typeLabel(connection) }}
              </span>
            </button>
            <span class="database-connection-item__actions">
              <button
                type="button"
                :aria-label="`编辑 ${connection.name}`"
                title="编辑连接"
                @click.stop="editConnection(connection)"
              >
                <Pencil :size="12" />
              </button>
              <button
                type="button"
                :aria-label="`删除 ${connection.name}`"
                title="删除连接"
                @click.stop="requestDelete(connection)"
              >
                <Trash2 :size="12" />
              </button>
            </span>
          </div>
        </template>

        <div v-else class="database-connections__state">
          <span class="database-connections__empty-icon">
            <Database :size="22" />
          </span>
          <strong>
            {{
              databases.connections.length === 0
                ? '还没有数据库连接'
                : '没有匹配的连接'
            }}
          </strong>
          <p>支持 MySQL 和 ClickHouse</p>
          <UiButton
            v-if="databases.connections.length === 0"
            size="sm"
            @click="addConnection"
          >
            <Plus :size="13" />
            添加连接
          </UiButton>
        </div>

        <UiPagination
          :page="connectionPage"
          :total="filteredConnections.length"
          label="数据库连接分页"
          compact
          @update:page="setConnectionPage"
        />
        </template>
        <div v-else class="database-connections__rail">
          <button
            type="button"
            aria-label="展开数据库连接配置"
            title="展开连接配置"
            @click="connectionsCollapsed = false"
          >
            <ChevronRight :size="13" />
          </button>
          <Database :size="15" />
        </div>
      </aside>

      <main
        class="database-query"
        :class="
          selectedConnection
            ? `database-query--${selectedConnection.type.toLocaleLowerCase()}`
            : ''
        "
      >
        <template v-if="selectedConnection">
          <header class="database-query__header">
            <div>
              <span
                class="database-query__type"
                :class="`database-query__type--${selectedConnection.type.toLocaleLowerCase()}`"
              >
                <img
                  v-if="selectedConnection.type === 'CLICKHOUSE'"
                  :src="clickhouseIcon"
                  alt=""
                />
                {{ typeLabel(selectedConnection) }}
              </span>
              <strong>{{ selectedConnection.name }}</strong>
              <small>{{ connectionTarget(selectedConnection) }}</small>
            </div>
            <span class="database-query__user">
              {{ selectedConnection.username }}
            </span>
          </header>

          <div class="database-query__body">
            <aside class="database-schema" aria-label="数据库表结构">
              <header>
                <span>数据库</span>
                <button
                  type="button"
                  aria-label="刷新数据库列表"
                  title="刷新数据库列表"
                  :disabled="schemasLoading"
                  @click="loadSchemas()"
                >
                  <RefreshCw
                    :class="{ 'database-spin': schemasLoading }"
                    :size="12"
                  />
                </button>
              </header>

              <div
                v-if="schemasLoading"
                class="database-schema__state"
              >
                <LoaderCircle class="database-spin" :size="14" />
                加载数据库…
              </div>
              <div
                v-else-if="schemasError"
                class="database-schema__state database-schema__state--error"
              >
                {{ schemasError }}
              </div>
              <div
                v-else-if="databaseSchemas.length === 0"
                class="database-schema__state"
              >
                没有可访问的数据库
              </div>
              <div v-else class="database-schema__databases">
                <div
                  v-for="schema in pagedSchemas"
                  :key="schema.name"
                  class="database-schema__database"
                >
                  <button
                    class="database-schema__database-button"
                    :class="{
                      'database-schema__database-button--active':
                        expandedDatabaseName === schema.name,
                    }"
                    type="button"
                    :aria-expanded="expandedDatabaseName === schema.name"
                    @click="toggleDatabase(schema.name)"
                  >
                    <ChevronRight
                      :class="{
                        'database-schema__chevron--open':
                          expandedDatabaseName === schema.name,
                      }"
                      :size="11"
                    />
                    <Database :size="12" />
                    <span>{{ schema.name }}</span>
                  </button>

                  <div
                    v-if="expandedDatabaseName === schema.name"
                    class="database-schema__database-content"
                  >
                    <label class="database-schema__search">
                      <Search :size="12" />
                      <input
                        v-model="tableSearchQuery"
                        type="search"
                        aria-label="搜索表"
                        placeholder="搜索表"
                      />
                    </label>
                    <div
                      v-if="tablesLoading"
                      class="database-schema__column-state"
                    >
                      <LoaderCircle
                        class="database-spin"
                        :size="12"
                      />
                      加载表…
                    </div>
                    <div
                      v-else-if="tablesError"
                      class="database-schema__column-state database-schema__column-state--error"
                    >
                      {{ tablesError }}
                    </div>
                    <div
                      v-else-if="filteredTables.length === 0"
                      class="database-schema__column-state"
                    >
                      没有可访问的表
                    </div>
                    <div v-else class="database-schema__tables">
                      <div
                        v-for="table in pagedTables"
                        :key="table.name"
                        class="database-schema__table"
                      >
                      <div class="database-schema__table-row">
                        <button
                          class="database-schema__table-button"
                          :class="{
                            'database-schema__table-button--active':
                              expandedTableName === table.name,
                          }"
                          type="button"
                          :aria-expanded="expandedTableName === table.name"
                          @click="toggleTable(table)"
                        >
                          <ChevronRight
                            :class="{
                              'database-schema__chevron--open':
                                expandedTableName === table.name,
                            }"
                            :size="11"
                          />
                          <Table2 :size="12" />
                          <span>{{ table.name }}</span>
                        </button>
                        <button
                          class="database-schema__copy"
                          type="button"
                          :aria-label="`复制表名 ${table.name}`"
                          :title="`复制表名 ${table.name}`"
                          @click.stop="
                            copyIdentifier(
                              table.name,
                              `table:${table.name}`,
                              '表名',
                            )
                          "
                        >
                          <Check
                            v-if="
                              copiedIdentifier === `table:${table.name}`
                            "
                            :size="11"
                          />
                          <Copy v-else :size="11" />
                        </button>
                      </div>

                      <div
                        v-if="expandedTableName === table.name"
                        class="database-schema__columns"
                      >
                        <div
                          v-if="columnsLoading"
                          class="database-schema__column-state"
                        >
                          <LoaderCircle
                            class="database-spin"
                            :size="12"
                          />
                          加载字段…
                        </div>
                        <div
                          v-else-if="columnsError"
                          class="database-schema__column-state database-schema__column-state--error"
                        >
                          {{ columnsError }}
                        </div>
                        <template v-else>
                          <button
                            v-for="column in pagedColumns"
                            :key="column.name"
                            type="button"
                            class="database-schema__column"
                            :title="`复制字段名 ${column.name}`"
                            :aria-label="`复制字段名 ${column.name}`"
                            @click="
                              copyIdentifier(
                                column.name,
                                `column:${table.name}:${column.name}`,
                                '字段名',
                              )
                            "
                          >
                            <span>{{ column.name }}</span>
                            <small>{{ column.type }}</small>
                            <em v-if="column.key">PK</em>
                            <em v-else-if="column.nullable">NULL</em>
                            <i>
                              <Check
                                v-if="
                                  copiedIdentifier ===
                                  `column:${table.name}:${column.name}`
                                "
                                :size="10"
                              />
                              <Copy v-else :size="10" />
                            </i>
                          </button>
                          <UiPagination
                            :page="columnPage"
                            :total="columns.length"
                            label="表字段分页"
                            compact
                            @update:page="setColumnPage"
                          />
                        </template>
                      </div>
                      </div>
                      <UiPagination
                        :page="tablePage"
                        :total="filteredTables.length"
                        label="数据库表分页"
                        compact
                        @update:page="setTablePage"
                      />
                    </div>
                  </div>
                </div>
                <UiPagination
                  :page="schemaPage"
                  :total="databaseSchemas.length"
                  label="数据库分页"
                  compact
                  @update:page="setSchemaPage"
                />
              </div>
            </aside>

            <div class="database-query__main">
          <section class="database-query__editor">
            <header>
              <div>
                <span>SQL 编辑器</span>
                <small>⌘ / Ctrl + Enter 执行</small>
              </div>
              <UiButton
                variant="primary"
                size="sm"
                :disabled="executing || !sql.trim()"
                @click="executeQuery"
              >
                <LoaderCircle
                  v-if="executing"
                  class="database-spin"
                  :size="13"
                />
                <Play v-else :size="13" />
                {{ executing ? '执行中…' : '执行' }}
              </UiButton>
            </header>
            <textarea
              :value="sql"
              spellcheck="false"
              aria-label="SQL 编辑器"
              placeholder="输入 SELECT、INSERT、UPDATE、DELETE 或其他 SQL"
              @input="
                updateSql(($event.target as HTMLTextAreaElement).value)
              "
              @keydown="handleEditorKeydown"
            />
          </section>

          <section class="database-query__result">
            <header>
              <span>
                <Table2 :size="14" />
                {{ resultContext || '执行结果' }}
              </span>
              <div v-if="queryResult">
                <small>
                  <Clock3 :size="11" />
                  {{ queryResult.elapsedMs }} ms
                </small>
                <small v-if="queryResult.resultSet">
                  <Rows3 :size="11" />
                  {{ queryResult.rows.length }} 行
                </small>
              </div>
            </header>

            <div
              v-if="executing || tableDataLoading"
              class="database-query__state"
            >
              <LoaderCircle class="database-spin" :size="16" />
              {{
                tableDataLoading
                  ? '正在读取表数据…'
                  : '正在执行 SQL…'
              }}
            </div>

            <div
              v-else-if="queryError"
              class="database-query__state database-query__state--error"
            >
              <CircleAlert :size="16" />
              <span>{{ queryError }}</span>
            </div>

            <template v-else-if="queryResult">
              <div
                v-if="!queryResult.resultSet"
                class="database-query__affected"
              >
                执行成功，影响 {{ queryResult.affectedRows }} 行
              </div>
              <div
                v-else-if="queryResult.columns.length === 0"
                class="database-query__state"
              >
                执行完成，没有返回列
              </div>
              <template v-else>
                <div class="database-result-table">
                  <table>
                    <thead>
                      <tr>
                        <th
                          v-for="column in queryResult.columns"
                          :key="column.name"
                        >
                          <span>{{ column.name }}</span>
                          <small>{{ column.type }}</small>
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="(row, rowIndex) in pagedResultRows"
                        :key="rowIndex"
                      >
                        <td
                          v-for="(cell, cellIndex) in row"
                          :key="cellIndex"
                          :class="{ 'database-result-table__null': cell === null }"
                          :title="cell ?? 'NULL'"
                        >
                          {{ displayCell(cell) }}
                        </td>
                      </tr>
                      <tr v-if="pagedResultRows.length === 0">
                        <td
                          :colspan="queryResult.columns.length"
                          class="database-result-table__empty"
                        >
                          执行返回 0 行
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <p v-if="queryResult.truncated" class="database-query__truncated">
                  结果已截断，仅保留前 500 行
                </p>
                <UiPagination
                  :page="resultPage"
                  :total="queryResult.rows.length"
                  label="SQL 执行结果分页"
                  @update:page="setResultPage"
                />
              </template>
            </template>

            <div v-else class="database-query__state">
              <Table2 :size="18" />
              执行 SQL 后在这里查看结果
            </div>
          </section>
            </div>
          </div>
        </template>

        <div v-else class="database-query__empty">
          <span>
            <Database :size="28" />
          </span>
          <strong>选择一个数据库连接</strong>
          <p>添加 MySQL 或 ClickHouse 连接后即可执行 SQL。</p>
          <UiButton variant="primary" @click="addConnection">
            <Plus :size="14" />
            添加数据库连接
          </UiButton>
        </div>
      </main>
    </div>

    <DatabaseConnectionDialog
      v-if="dialogOpen"
      :workspace-id="workspaces.activeWorkspaceId"
      :connection="editingConnection"
      @close="dialogOpen = false"
      @saved="connectionSaved"
      @notify="emit('notify', $event)"
    />

    <UiConfirmDialog
      v-if="deleteTarget"
      title="删除数据库连接？"
      :description="`「${deleteTarget.name}」的连接配置和加密密码将从当前工作空间删除。`"
      warning="不会删除目标数据库中的任何数据。"
      confirm-label="删除连接"
      busy-label="正在删除…"
      :busy="deleting"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />

    <UiConfirmDialog
      v-if="confirmExecuteOpen"
      title="执行 SQL 写操作？"
      description="当前 SQL 可能修改数据库结构或数据。"
      warning="执行后将立即作用于目标数据库，此操作可能无法撤销。"
      confirm-label="确认执行"
      busy-label="执行中…"
      :busy="executing"
      @close="confirmExecuteOpen = false"
      @confirm="performExecute"
    />
  </section>
</template>

<style scoped>
.database-workspace {
  --database-accent: var(--system-blue);
  --database-accent-soft: color-mix(
    in srgb,
    var(--database-accent) 9%,
    var(--canvas-raised)
  );
  --database-accent-border: color-mix(
    in srgb,
    var(--database-accent) 22%,
    var(--line-soft)
  );
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-2);
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  animation: database-enter var(--duration-window) var(--ease-apple) both;
}

.database-toolbar {
  display: flex;
  min-height: 50px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  background:
    linear-gradient(
      180deg,
      var(--canvas-raised),
      color-mix(in srgb, var(--surface-inset) 32%, var(--canvas-raised))
    );
  border: 1px solid var(--line-soft);
  border-radius: 9px;
  box-shadow: var(--shadow-panel);
}

.database-toolbar__count {
  flex: 1;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.database-search {
  display: flex;
  width: min(100%, 250px);
  height: var(--control-md);
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  color: var(--text-subtle);
  background: color-mix(
    in srgb,
    var(--surface-inset) 58%,
    var(--canvas-raised)
  );
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.database-search:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.database-search input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.database-search input:focus-visible {
  outline: none;
}

.database-load-error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(in srgb, var(--system-red) 7%, var(--canvas-raised));
  border-radius: var(--radius-control);
}

.database-load-error span {
  flex: 1;
}

.database-load-error button {
  color: inherit;
  font-size: inherit;
  background: transparent;
}

.database-layout {
  display: grid;
  min-height: 620px;
  grid-template-columns: 270px minmax(0, 1fr);
  gap: var(--space-3);
}

.database-layout--collapsed {
  grid-template-columns: 42px minmax(0, 1fr);
}

.database-connections,
.database-query {
  min-width: 0;
  overflow: hidden;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: 9px;
  box-shadow: var(--shadow-panel);
}

.database-connections {
  display: flex;
  flex-direction: column;
}

.database-connections__header {
  display: flex;
  min-height: 38px;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 0 12px;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  background: color-mix(
    in srgb,
    var(--surface-inset) 48%,
    var(--canvas-raised)
  );
  border-bottom: 1px solid var(--line-soft);
}

.database-connections__header button,
.database-connections__rail button {
  display: grid;
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.database-connections__header button:hover,
.database-connections__rail button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.database-connections__rail {
  display: flex;
  min-height: 100%;
  align-items: center;
  padding-top: 7px;
  color: var(--text-subtle);
  flex-direction: column;
  gap: var(--space-3);
}

.database-connections__state {
  display: flex;
  min-height: 220px;
  align-items: center;
  justify-content: center;
  padding: var(--space-5);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
  flex-direction: column;
  gap: var(--space-2);
}

.database-connections__state strong {
  color: var(--text);
  font-size: var(--font-body);
}

.database-connections__state p {
  margin: 0 0 var(--space-2);
}

.database-connections__empty-icon {
  display: grid;
  width: 42px;
  height: 42px;
  margin-bottom: var(--space-1);
  color: var(--text-muted);
  background: var(--surface-inset);
  border-radius: var(--radius-panel);
  place-items: center;
}

.database-connection-item {
  --database-accent: var(--system-blue);
  --database-accent-soft: color-mix(
    in srgb,
    var(--database-accent) 9%,
    var(--canvas-raised)
  );
  position: relative;
  min-width: 0;
  border-bottom: 1px solid var(--line-soft);
  transition: background var(--duration-fast) var(--ease-apple);
}

.database-connection-item--clickhouse {
  --database-accent: color-mix(
    in srgb,
    var(--system-orange) 64%,
    var(--text)
  );
}

.database-connection-item:hover {
  background: color-mix(
    in srgb,
    var(--database-accent) 4%,
    transparent
  );
}

.database-connection-item--active {
  background: var(--database-accent-soft);
  box-shadow: inset 2px 0 0 var(--database-accent);
}

.database-connection-item--active:hover {
  background: var(--database-accent-soft);
}

.database-connection-item__main {
  display: grid;
  width: 100%;
  min-width: 0;
  min-height: 64px;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  color: var(--text);
  text-align: left;
  background: transparent;
}

.database-connection-item__icon {
  display: grid;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-control);
  place-items: center;
}

.database-connection-item__icon--mysql {
  color: var(--database-accent);
  background: color-mix(
    in srgb,
    var(--database-accent) 12%,
    var(--surface-inset)
  );
}

.database-connection-item__icon--clickhouse {
  color: var(--database-accent);
  background: color-mix(
    in srgb,
    var(--database-accent) 12%,
    var(--surface-inset)
  );
}

.database-connection-item__icon img {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.database-connection-item__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.database-connection-item__copy strong,
.database-connection-item__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.database-connection-item__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.database-connection-item__copy small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.database-connection-item__type {
  align-self: start;
  padding: 2px 5px;
  color: var(--database-accent);
  font-size: 11px;
  font-weight: var(--weight-emphasis);
  background: color-mix(
    in srgb,
    var(--database-accent) 9%,
    var(--canvas-raised)
  );
  border: 1px solid color-mix(
    in srgb,
    var(--database-accent) 16%,
    transparent
  );
  border-radius: var(--radius-badge);
}

.database-connection-item__actions {
  position: absolute;
  right: var(--space-2);
  bottom: var(--space-1);
  display: flex;
  gap: 2px;
  opacity: 0;
}

.database-connection-item:hover .database-connection-item__actions,
.database-connection-item:focus-within .database-connection-item__actions,
.database-connection-item--active .database-connection-item__actions {
  opacity: 1;
}

.database-connection-item__actions button {
  display: grid;
  width: 20px;
  height: 20px;
  padding: 0;
  color: var(--text-subtle);
  background: var(--canvas-raised);
  border-radius: var(--radius-badge);
  cursor: pointer;
  place-items: center;
}

.database-connection-item__actions button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.database-query {
  --database-accent: var(--system-blue);
  --database-accent-soft: color-mix(
    in srgb,
    var(--database-accent) 8%,
    var(--canvas-raised)
  );
  --database-accent-border: color-mix(
    in srgb,
    var(--database-accent) 22%,
    var(--line-soft)
  );
  display: flex;
  flex-direction: column;
}

.database-query--clickhouse {
  --database-accent: color-mix(
    in srgb,
    var(--system-orange) 64%,
    var(--text)
  );
}

.database-query__body {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: 210px minmax(0, 1fr);
}

.database-query__main {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.database-schema {
  display: flex;
  min-height: 0;
  flex-direction: column;
  background: color-mix(
    in srgb,
    var(--surface-inset) 55%,
    var(--canvas-raised)
  );
  border-right: 1px solid var(--line-soft);
}

.database-schema > header {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 0 12px;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  background: color-mix(
    in srgb,
    var(--surface-inset) 44%,
    var(--canvas-raised)
  );
  border-bottom: 1px solid var(--line-soft);
}

.database-schema > header button {
  display: grid;
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.database-schema > header button:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.database-schema__search {
  display: flex;
  height: 28px;
  align-items: center;
  gap: 5px;
  margin: 7px;
  padding: 0 7px;
  color: var(--text-subtle);
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.database-schema__search:focus-within {
  border-color: var(--database-accent);
  box-shadow: 0 0 0 3px color-mix(
    in srgb,
    var(--database-accent) 17%,
    transparent
  );
}

.database-schema__search input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-caption);
  background: transparent;
  border: 0;
}

.database-schema__search input:focus-visible {
  outline: none;
}

.database-schema__state {
  display: flex;
  min-height: 110px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: var(--space-3);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
}

.database-schema__state--error {
  color: var(--system-red);
}

.database-schema__databases {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
}

.database-schema__database-button {
  display: grid;
  width: 100%;
  min-height: 32px;
  grid-template-columns: 12px 14px minmax(0, 1fr);
  align-items: center;
  gap: 4px;
  padding: 4px 7px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  text-align: left;
  background: transparent;
  border-bottom: 1px solid var(--line-soft);
}

.database-schema__database-button:hover {
  color: var(--database-accent);
  background: color-mix(
    in srgb,
    var(--database-accent) 5%,
    transparent
  );
}

.database-schema__database-button--active {
  color: var(--database-accent);
  background: var(--database-accent-soft);
  box-shadow: inset 2px 0 0 var(--database-accent);
}

.database-schema__database-button > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.database-schema__database-content {
  padding-left: 4px;
}

.database-schema__tables {
  overflow: visible;
}

.database-schema__table {
  border-bottom: 1px solid var(--line-soft);
}

.database-schema__table-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 24px;
  align-items: center;
}

.database-schema__table-button {
  display: grid;
  width: 100%;
  min-height: 30px;
  grid-template-columns: 12px 14px minmax(0, 1fr);
  align-items: center;
  gap: 4px;
  padding: 3px 7px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  text-align: left;
  background: transparent;
  padding-left: 14px;
}

.database-schema__copy {
  display: grid;
  width: 22px;
  height: 22px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  opacity: 0.45;
  place-items: center;
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple),
    opacity var(--duration-fast) var(--ease-apple);
}

.database-schema__table-row:hover .database-schema__copy,
.database-schema__copy:focus-visible {
  opacity: 1;
}

.database-schema__copy:hover {
  color: var(--database-accent);
  background: var(--hover-bg);
}

.database-schema__table-button:hover {
  color: var(--database-accent);
  background: color-mix(
    in srgb,
    var(--database-accent) 5%,
    transparent
  );
}

.database-schema__table-button--active {
  color: var(--database-accent);
  background: var(--database-accent-soft);
}

.database-schema__table-button > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.database-schema__chevron--open {
  transform: rotate(90deg);
}

.database-schema__columns {
  padding: 2px 5px 5px 20px;
  background: color-mix(
    in srgb,
    var(--canvas-raised) 72%,
    transparent
  );
}

.database-schema__column-state {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.database-schema__column-state--error {
  color: var(--system-red);
}

.database-schema__column {
  display: grid;
  width: 100%;
  min-height: 28px;
  grid-template-columns: minmax(0, 1fr) auto auto 16px;
  align-items: center;
  gap: 4px;
  padding: 2px 3px;
  color: var(--text-muted);
  text-align: left;
  background: transparent;
}

.database-schema__column:hover {
  color: var(--text);
}

.database-schema__column > span {
  overflow: hidden;
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.database-schema__column small {
  max-width: 76px;
  overflow: hidden;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.database-schema__column em {
  color: var(--database-accent);
  font-size: 11px;
  font-style: normal;
}

.database-schema__column i {
  display: grid;
  width: 16px;
  height: 16px;
  color: var(--text-subtle);
  font-style: normal;
  opacity: 0.42;
  place-items: center;
  transition:
    color var(--duration-fast) var(--ease-apple),
    opacity var(--duration-fast) var(--ease-apple);
}

.database-schema__column:hover i {
  color: var(--database-accent);
  opacity: 1;
}

.database-query__header {
  display: flex;
  min-height: 62px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4);
  background:
    linear-gradient(
      90deg,
      var(--database-accent-soft),
      transparent 72%
    );
  border-bottom: 1px solid var(--database-accent-border);
}

.database-query__header > div {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.database-query__header strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.database-query__header small,
.database-query__user {
  overflow: hidden;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.database-query__type {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  font-size: var(--font-caption);
  border-radius: var(--radius-badge);
}

.database-query__type img {
  width: 11px;
  height: 11px;
  object-fit: contain;
}

.database-query__type--mysql {
  color: var(--database-accent);
  background: color-mix(
    in srgb,
    var(--database-accent) 10%,
    transparent
  );
}

.database-query__type--clickhouse {
  color: var(--database-accent);
  background: color-mix(
    in srgb,
    var(--database-accent) 10%,
    transparent
  );
}

.database-query__editor {
  padding: var(--space-3) var(--space-4);
  background: color-mix(
    in srgb,
    var(--canvas-raised) 96%,
    var(--surface-inset)
  );
  border-bottom: 1px solid var(--line-soft);
}

.database-query__editor > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.database-query__editor > header > div {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.database-query__editor > header span {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.database-query__editor > header small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.database-query__editor textarea {
  width: 100%;
  min-height: 150px;
  padding: var(--space-3);
  resize: vertical;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  line-height: 1.65;
  background:
    linear-gradient(
      180deg,
      color-mix(
        in srgb,
        var(--surface-inset) 74%,
        var(--canvas-raised)
      ),
      var(--surface-inset)
    );
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
  tab-size: 2;
}

.database-query__editor textarea:focus {
  border-color: var(--database-accent);
  outline: none;
  box-shadow: 0 0 0 3px color-mix(
    in srgb,
    var(--database-accent) 18%,
    transparent
  );
}

.database-query__result {
  display: flex;
  min-height: 280px;
  flex: 1;
  flex-direction: column;
}

.database-query__result > header {
  display: flex;
  min-height: 40px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  background: color-mix(
    in srgb,
    var(--surface-inset) 42%,
    var(--canvas-raised)
  );
  border-bottom: 1px solid var(--line-soft);
}

.database-query__result > header > span,
.database-query__result > header > div,
.database-query__result > header small {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.database-query__result > header > span {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.database-query__result > header > div {
  color: var(--text-subtle);
  gap: var(--space-2);
}

.database-query__result > header small {
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.database-query__state {
  display: flex;
  min-height: 220px;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-5);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
}

.database-query__state--error {
  color: var(--system-red);
}

.database-query__affected {
  padding: var(--space-5);
  color: var(--system-green);
  font-size: var(--font-caption);
  text-align: center;
}

.database-result-table {
  max-width: 100%;
  overflow: auto;
}

.database-result-table table {
  width: max-content;
  min-width: 100%;
  border-collapse: collapse;
}

.database-result-table th,
.database-result-table td {
  max-width: 320px;
  padding: 9px 12px;
  overflow: hidden;
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-right: 1px solid var(--line-soft);
  border-bottom: 1px solid var(--line-soft);
}

.database-result-table th {
  position: sticky;
  top: 0;
  color: var(--text);
  background: color-mix(
    in srgb,
    var(--surface-inset) 58%,
    var(--canvas-raised)
  );
}

.database-result-table th span,
.database-result-table th small {
  display: block;
}

.database-result-table th small {
  margin-top: 2px;
  color: var(--text-subtle);
  font-weight: var(--weight-regular);
}

.database-result-table td {
  color: var(--text-muted);
}

.database-result-table tbody tr:hover {
  background: color-mix(
    in srgb,
    var(--database-accent) 4%,
    transparent
  );
}

.database-result-table__null {
  color: var(--text-subtle) !important;
  font-style: italic;
}

.database-result-table__empty {
  height: 120px;
  color: var(--text-subtle) !important;
  text-align: center;
}

.database-query__truncated {
  margin: 0;
  padding: 7px var(--space-4);
  color: var(--system-orange);
  font-size: var(--font-caption);
  background: color-mix(
    in srgb,
    var(--system-orange) 6%,
    transparent
  );
}

.database-query__empty {
  display: flex;
  min-height: 620px;
  align-items: center;
  justify-content: center;
  padding: var(--space-5);
  color: var(--text-subtle);
  text-align: center;
  flex-direction: column;
  gap: var(--space-2);
}

.database-query__empty > span {
  display: grid;
  width: 52px;
  height: 52px;
  margin-bottom: var(--space-1);
  color: var(--text-muted);
  background: var(--surface-inset);
  border-radius: var(--radius-panel);
  place-items: center;
}

.database-query__empty strong {
  color: var(--text);
  font-size: var(--font-body);
}

.database-query__empty p {
  margin: 0 0 var(--space-2);
  font-size: var(--font-caption);
}

.database-spin {
  animation: database-spin 900ms linear infinite;
}

@keyframes database-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes database-enter {
  from {
    opacity: 0;
    transform: translateY(4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 980px) {
  .database-layout {
    grid-template-columns: 230px minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .database-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .database-toolbar__count {
    flex-basis: 100%;
  }

  .database-search {
    width: auto;
    flex: 1;
  }

  .database-layout {
    grid-template-columns: 1fr;
  }

  .database-layout--collapsed {
    grid-template-columns: 36px minmax(0, 1fr);
  }

  .database-connections {
    max-height: 320px;
    overflow-y: auto;
  }

  .database-query__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .database-query__header > div {
    flex-wrap: wrap;
  }

  .database-query__user {
    padding-left: 2px;
  }

  .database-query__editor,
  .database-query__result > header {
    padding-right: var(--space-3);
    padding-left: var(--space-3);
  }

  .database-query__body {
    grid-template-columns: 1fr;
  }

  .database-schema {
    max-height: 280px;
    border-right: 0;
    border-bottom: 1px solid var(--line-soft);
  }

  .database-schema__tables {
    max-height: 210px;
  }

  .database-query__empty {
    min-height: 360px;
  }
}
</style>
