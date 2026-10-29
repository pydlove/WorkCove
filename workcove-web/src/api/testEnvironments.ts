import {
  deleteNoContentWithHeaders,
  get,
  postJsonWithHeaders,
  putJsonWithHeaders,
} from '@/api/http'
import type {
  TestEnvironment,
  TestEnvironmentCredentials,
  TestEnvironmentInput,
} from '@/types/testEnvironment'

const ACTION_HEADER = {
  'X-WorkCove-Action': 'manage-test-environments',
}

function requestBody(input: TestEnvironmentInput) {
  return {
    workspaceId: input.workspaceId,
    name: input.name,
    url: input.url,
    username: input.username,
    password: input.password,
  }
}

export const listTestEnvironments = (workspaceId: string) =>
  get<TestEnvironment[]>(
    `/development/test-environments?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const createTestEnvironment = (
  input: TestEnvironmentInput,
) =>
  postJsonWithHeaders<TestEnvironment>(
    '/development/test-environments',
    requestBody(input),
    ACTION_HEADER,
  )

export const updateTestEnvironment = (
  input: TestEnvironmentInput,
) =>
  putJsonWithHeaders<TestEnvironment>(
    `/development/test-environments/${input.id}`,
    requestBody(input),
    ACTION_HEADER,
  )

export const deleteTestEnvironment = (
  id: number,
  workspaceId: string,
) =>
  deleteNoContentWithHeaders(
    `/development/test-environments/${id}?workspaceId=${encodeURIComponent(workspaceId)}`,
    ACTION_HEADER,
  )

export const readTestEnvironmentCredentials = (
  id: number,
  workspaceId: string,
) =>
  postJsonWithHeaders<TestEnvironmentCredentials>(
    `/development/test-environments/${id}/credentials?workspaceId=${encodeURIComponent(workspaceId)}`,
    {},
    ACTION_HEADER,
  )
