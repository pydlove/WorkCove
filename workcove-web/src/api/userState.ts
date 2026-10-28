import { get, putJsonNoContentWithHeaders } from '@/api/http'

const ACTION_HEADER = { 'X-WorkCove-Action': 'manage-user-state' }

export function getUserState() {
  return get<Record<string, string>>('/development/user-state')
}

export async function saveUserState(
  stateKey: string,
  payload: unknown,
) {
  await putJsonNoContentWithHeaders(
    `/development/user-state/${encodeURIComponent(stateKey)}`,
    payload,
    ACTION_HEADER,
  )
}
