package com.pydlove.workcove.development.domain.port;

import java.time.Duration;

public interface RemoteDeploymentGateway {

    Result execute(
            SshTerminalGateway.Connection connection,
            SshTerminalGateway.HostKey expectedHostKey,
            String remoteDirectory,
            String scriptFilename,
            String scriptContent,
            Duration timeout);

    record Result(
            boolean timedOut,
            Integer exitCode,
            String output,
            SshTerminalGateway.HostKey hostKey) {
    }
}
