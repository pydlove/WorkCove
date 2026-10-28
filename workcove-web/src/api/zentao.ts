import {
  deleteNoContentWithHeaders,
  get,
  postJsonWithHeaders,
  putJsonWithHeaders,
} from '@/api/http'
import type {
  ZentaoBug,
  ZentaoConnection,
  ZentaoConnectionInput,
  ZentaoLocalResolveInput,
  ZentaoProject,
  ZentaoProjectInput,
  ZentaoResolveInput,
  ZentaoSyncResult,
} from '@/types/zentao'
import type {
  ZentaoTestEvidence,
  ZentaoTestRun,
} from '@/types/zentaoTest'

const ACTION_HEADER = { 'X-WorkCove-Action': 'manage-zentao' }

export const getZentaoConnection = (workspaceId: string) =>
  get<ZentaoConnection>(
    `/development/zentao/connection?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const saveZentaoConnection = (input: ZentaoConnectionInput) =>
  putJsonWithHeaders<ZentaoConnection>(
    '/development/zentao/connection',
    input,
    ACTION_HEADER,
  )

export const listZentaoProjects = (input: ZentaoProjectInput) =>
  postJsonWithHeaders<ZentaoProject[]>(
    '/development/zentao/projects',
    input,
    ACTION_HEADER,
    45_000,
  )

export const syncZentaoBugs = (workspaceId: string) =>
  postJsonWithHeaders<ZentaoSyncResult>(
    '/development/zentao/sync',
    { workspaceId },
    ACTION_HEADER,
    120_000,
  )

export const listZentaoBugs = (workspaceId: string) =>
  get<ZentaoBug[]>(
    `/development/zentao/bugs?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const resolveZentaoBug = (
  bugId: number,
  input: ZentaoResolveInput,
) =>
  postJsonWithHeaders<ZentaoBug>(
    `/development/zentao/bugs/${bugId}/resolve`,
    input,
    ACTION_HEADER,
    60_000,
  )

export const resolveZentaoBugLocally = (
  bugId: number,
  input: ZentaoLocalResolveInput,
) =>
  putJsonWithHeaders<ZentaoBug>(
    `/development/zentao/bugs/${bugId}/local-resolve`,
    input,
    ACTION_HEADER,
  )

export const deleteZentaoBug = (
  bugId: number,
  workspaceId: string,
) =>
  deleteNoContentWithHeaders(
    `/development/zentao/bugs/${bugId}?workspaceId=${encodeURIComponent(workspaceId)}`,
    ACTION_HEADER,
  )

export const prepareZentaoTestRun = (
  bugId: number,
  workspaceId: string,
) =>
  postJsonWithHeaders<ZentaoTestRun>(
    `/development/zentao/bugs/${bugId}/test-runs`,
    { workspaceId },
    ACTION_HEADER,
  )

export const listZentaoTestEvidence = (
  bugId: number,
  runId: string,
  workspaceId: string,
) =>
  get<ZentaoTestEvidence[]>(
    `/development/zentao/bugs/${bugId}/test-runs/`
      + `${encodeURIComponent(runId)}/evidence`
      + `?workspaceId=${encodeURIComponent(workspaceId)}`,
  )
