import { postJsonWithHeaders } from '@/api/http'
import type {
  DeploymentConfig,
  DeploymentExecutionResponse,
} from '@/types/deployment'

const ACTION_HEADER = { 'X-WorkCove-Action': 'deploy-script' }
const EXECUTION_TIMEOUT_MS = 11 * 60 * 1000

export const executeDeploymentScript = (
  deployment: DeploymentConfig,
) =>
  postJsonWithHeaders<DeploymentExecutionResponse>(
    '/development/deployments/execute',
    {
      workspaceId: deployment.workspaceId,
      serverId: deployment.serverId,
      remoteDirectory: deployment.remoteDirectory,
      scriptFilename: deployment.scriptFilename,
      scriptContent: deployment.scriptContent,
    },
    ACTION_HEADER,
    EXECUTION_TIMEOUT_MS,
  )
