package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.RemoteDeploymentGateway;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.command.AbstractCommandSupport;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SshjRemoteDeploymentGatewayIntegrationTest {

    private static final String USERNAME = "tester";
    private static final String PASSWORD = "test-password";

    private static SshServer sshServer;
    private static Path remoteDirectory;

    private final SshjRemoteDeploymentGateway gateway =
            new SshjRemoteDeploymentGateway();

    @BeforeAll
    static void startSshServer() throws Exception {
        remoteDirectory = Files.createTempDirectory(
                "workcove-deployment-test-");
        Path hostKey = Files.createTempFile("workcove-deploy-sshd-", ".key");
        sshServer = SshServer.setUpDefaultServer();
        sshServer.setHost("127.0.0.1");
        sshServer.setPort(0);
        sshServer.setKeyPairProvider(
                new SimpleGeneratorHostKeyProvider(hostKey));
        sshServer.setPasswordAuthenticator(
                (username, password, session) ->
                        USERNAME.equals(username)
                                && PASSWORD.equals(password));
        sshServer.setCommandFactory((channel, command) ->
                new AbstractCommandSupport(command, null) {
                    @Override
                    public void run() {
                        try {
                            String script = new String(
                                    getInputStream().readAllBytes(),
                                    StandardCharsets.UTF_8);
                            String output = "COMMAND:" + getCommand()
                                    + "\nSCRIPT:" + script;
                            getOutputStream().write(
                                    output.getBytes(StandardCharsets.UTF_8));
                            getOutputStream().flush();
                            onExit(0);
                        } catch (Exception exception) {
                            onExit(1, exception.getMessage());
                        }
                    }
                });
        sshServer.start();
    }

    @AfterAll
    static void stopSshServer() throws Exception {
        if (sshServer != null) sshServer.stop(true);
    }

    @Test
    void executesScriptThroughSshStdin() {
        RemoteDeploymentGateway.Result result = gateway.execute(
                new SshTerminalGateway.Connection(
                        "127.0.0.1",
                        sshServer.getPort(),
                        USERNAME,
                        PASSWORD),
                null,
                remoteDirectory.toString(),
                "deploy.sh",
                "#!/usr/bin/env bash\n"
                        + "printf 'deployed:%s\\n' "
                        + "\"$WORKCOVE_DEPLOY_DIRECTORY\"\n",
                Duration.ofSeconds(10));

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains(remoteDirectory.toString()));
        assertTrue(result.output().contains(
                "REGENT_DEPLOY_DIRECTORY=\"$(pwd -P)\""));
        assertTrue(result.output().contains(
                "REGENT_DEPLOY_SCRIPT='deploy.sh'"));
        assertTrue(result.output().contains(
                "printf 'deployed:%s\\n'"));
        assertNotNull(result.hostKey());
    }
}
