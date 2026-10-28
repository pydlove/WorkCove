const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { DatabaseSync } = require('node:sqlite')

const VALID_RANGES = new Set(['today', '7d', '30d', 'all'])

function rangeStart(range) {
  if (range === 'all') return null
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  if (range === '7d') start.setDate(start.getDate() - 6)
  if (range === '30d') start.setDate(start.getDate() - 29)
  return Math.floor(start.getTime() / 1000)
}

function emptyResult(range, reason) {
  return {
    available: false,
    source: 'cc-switch',
    range,
    reason,
    costUsd: null,
    requestCount: 0,
    successCount: 0,
    inputTokens: 0,
    outputTokens: 0,
    cacheReadTokens: 0,
    cacheCreationTokens: 0,
    totalTokens: 0,
    cacheHitRate: null,
    updatedAt: new Date().toISOString(),
  }
}

function getCcSwitchUsage(requestedRange = 'today') {
  const range = VALID_RANGES.has(requestedRange)
    ? requestedRange
    : 'today'
  const databasePath = path.join(
    os.homedir(),
    '.cc-switch',
    'cc-switch.db',
  )
  if (!fs.existsSync(databasePath)) {
    return emptyResult(range, '未找到 CC Switch 数据库')
  }

  let database
  try {
    database = new DatabaseSync(databasePath, {
      readOnly: true,
      timeout: 3000,
    })
    database.exec('PRAGMA busy_timeout = 3000')
    const start = rangeStart(range)
    const where = start === null
      ? ''
      : `
        WHERE (
          CASE
            WHEN created_at > 100000000000 THEN created_at / 1000
            ELSE created_at
          END
        ) >= ?
      `
    const statement = database.prepare(`
      SELECT
        COUNT(*) AS request_count,
        COALESCE(SUM(
          CASE WHEN status_code >= 200 AND status_code < 400 THEN 1 ELSE 0 END
        ), 0) AS success_count,
        COALESCE(SUM(input_tokens), 0) AS input_tokens,
        COALESCE(SUM(output_tokens), 0) AS output_tokens,
        COALESCE(SUM(cache_read_tokens), 0) AS cache_read_tokens,
        COALESCE(SUM(cache_creation_tokens), 0) AS cache_creation_tokens,
        COALESCE(SUM(CAST(total_cost_usd AS REAL)), 0) AS total_cost_usd
      FROM proxy_request_logs
      ${where}
    `)
    const row = start === null ? statement.get() : statement.get(start)
    const inputTokens = Number(row.input_tokens ?? 0)
    const outputTokens = Number(row.output_tokens ?? 0)
    const cacheReadTokens = Number(row.cache_read_tokens ?? 0)
    const cacheHitRate = inputTokens > 0
      ? Math.min(100, Math.max(0, (cacheReadTokens / inputTokens) * 100))
      : null
    return {
      available: true,
      source: 'cc-switch',
      range,
      reason: '',
      costUsd: Number(row.total_cost_usd ?? 0),
      requestCount: Number(row.request_count ?? 0),
      successCount: Number(row.success_count ?? 0),
      inputTokens,
      outputTokens,
      cacheReadTokens,
      cacheCreationTokens: Number(row.cache_creation_tokens ?? 0),
      totalTokens: inputTokens + outputTokens,
      cacheHitRate,
      updatedAt: new Date().toISOString(),
    }
  } catch (error) {
    return emptyResult(
      range,
      error instanceof Error
        ? `无法读取 CC Switch 数据库：${error.message}`
        : '无法读取 CC Switch 数据库',
    )
  } finally {
    database?.close()
  }
}

module.exports = {
  getCcSwitchUsage,
}
