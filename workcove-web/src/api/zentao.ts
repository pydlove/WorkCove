import {
  deleteNoContentWithHeaders,
  get,
  postJsonWithHeaders,
  putJsonWithHeaders,
} from '@/api/http'
import type {
  ZentaoBug,
  ZentaoBuild,
  ZentaoBuildCreateInput,
  ZentaoBuildOptions,
  ZentaoConnection,
  ZentaoConnectionInput,
  ZentaoCreatedBuild,
  ZentaoCreatedTestTask,
  ZentaoLocalResolveInput,
  ZentaoProject,
  ZentaoProjectInput,
  ZentaoResolveInput,
  ZentaoSystem,
  ZentaoSyncResult,
  ZentaoTestTaskCreateInput,
  ZentaoTestTaskOptions,
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

export const listZentaoBuilds = (workspaceId: string) =>
  get<ZentaoBuild[]>(
    `/development/zentao/builds?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const listZentaoBuildOptions = (workspaceId: string) =>
  get<ZentaoBuildOptions>(
    `/development/zentao/build-options?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const listZentaoSystems = (
  workspaceId: string,
  productId: string,
) =>
  get<ZentaoSystem[]>(
    `/development/zentao/systems?workspaceId=${encodeURIComponent(workspaceId)}`
      + `&productId=${encodeURIComponent(productId)}`,
  )

export const listZentaoTestTaskOptions = (
  workspaceId: string,
  productId: string,
) =>
  get<ZentaoTestTaskOptions>(
    `/development/zentao/test-task-options?workspaceId=${encodeURIComponent(workspaceId)}`
      + `&productId=${encodeURIComponent(productId)}`,
  )

export const createZentaoBuild = (input: ZentaoBuildCreateInput) =>
  postJsonWithHeaders<ZentaoCreatedBuild>(
    '/development/zentao/builds',
    input,
    ACTION_HEADER,
    60_000,
  )

export const createZentaoTestTask = (
  buildId: number,
  input: ZentaoTestTaskCreateInput,
) =>
  postJsonWithHeaders<ZentaoCreatedTestTask>(
    `/development/zentao/builds/${buildId}/test-tasks`,
    input,
    ACTION_HEADER,
    60_000,
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

export const confirmZentaoBug = (
  bugId: number,
  workspaceId: string,
) =>
  putJsonWithHeaders<ZentaoBug>(
    `/development/zentao/bugs/${bugId}/confirm`,
    { workspaceId },
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

export const appendZentaoTestComment = (
  bugId: number,
  input: {
    workspaceId: string
    runId: string
    environment: string
    proof: string
    screenshots: string[]
  },
) =>
  postJsonWithHeaders<{ alreadyExists: boolean }>(
    `/development/zentao/bugs/${bugId}/test-comment`,
    input,
    ACTION_HEADER,
    60_000,
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
