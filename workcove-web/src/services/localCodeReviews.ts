import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type { CodeReviewRecord } from '@/types/codeReview'

const STORAGE_KEY = 'workcove.code-reviews.v1'
const MAX_RECORDS = 300

export function loadLocalCodeReviews(email: string) {
  const records = readUserScopedValue<CodeReviewRecord[]>(
    STORAGE_KEY,
    email,
  )
  return Array.isArray(records)
    ? records
        .filter(
          (record) =>
            record &&
            typeof record.id === 'string' &&
            typeof record.repositoryPath === 'string' &&
            typeof record.branch === 'string' &&
            Array.isArray(record.findings),
        )
        .map((record) => ({
          ...record,
          reviewSince:
            typeof record.reviewSince === 'string'
              ? record.reviewSince
              : '',
          reviewUntil:
            typeof record.reviewUntil === 'string'
              ? record.reviewUntil
              : '',
          changedFiles: Array.isArray(record.changedFiles)
            ? record.changedFiles
                .filter((file): file is string => typeof file === 'string')
                .map((file) => file.trim())
                .filter(Boolean)
            : [],
        }))
        .slice(0, MAX_RECORDS)
    : []
}

export function saveLocalCodeReviews(
  email: string,
  records: CodeReviewRecord[],
) {
  writeUserScopedValue(
    STORAGE_KEY,
    email,
    records.slice(0, MAX_RECORDS),
  )
}
