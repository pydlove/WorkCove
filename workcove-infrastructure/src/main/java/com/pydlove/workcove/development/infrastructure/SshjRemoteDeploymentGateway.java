package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.RemoteDeploymentGateway;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.shared.domain.DomainException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class SshjRemoteDeploymentGateway
        implements RemoteDeploymentGateway {

    private static final int MAX_LOG_CHARS = 1024 * 1024;

    private final SshjConnector connector = new SshjConnector();

    @Override
    public Result execute(
            SshTerminalGateway.Connection connection,
            SshTerminalGateway.HostKey expectedHostKey,
            String remoteDirectory,
            String scriptFilename,
            String scriptContent,
            Duration timeout) {
        SshjConnector.ConnectedClient connected =
                connector.connect(connection, expectedHostKey);
        SSHClient client = connected.client();
        Session session = null;
        try {
            session = client.startSession();
            Session.Command command = session.exec(
                    remoteCommand(remoteDirectory, scriptFilename));
            LogCollector output = new LogCollector(MAX_LOG_CHARS);
            Thread stdout = startReader(
                    command.getInputStream(),
                    output,
                    "workcove-deploy-stdout");
            Thread stderr = startReader(
                    command.getErrorStream(),
                    output,
                    "workcove-deploy-stderr");

            try (OutputStream input = command.getOutputStream()) {
                input.write(scriptContent.getBytes(StandardCharsets.UTF_8));
                input.write('\n');
                input.flush();
            } catch (IOException exception) {
                output.append(
                        "[平台] 脚本写入 SSH 通道失败："
                                + exception.getMessage()
                                + "\n");
            }

            boolean timedOut = waitFor(command, timeout);
            if (timedOut) {
                output.append("[平台] 部署超过 10 分钟，连接已终止\n");
                command.close();
            }
            stdout.join(2_000);
            stderr.join(2_000);
            Integer exitCode = timedOut ? null : command.getExitStatus();
            return new Result(
                    timedOut,
                    exitCode,
                    output.value(),
                    connected.hostKey());
        } catch (IOException exception) {
            throw new DomainException(
                    "无法执行远程部署脚本：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException("部署执行被中断");
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (IOException ignored) {
                }
            }
            SshjConnector.closeQuietly(client);
        }
    }

    private static boolean waitFor(
            Session.Command command,
            Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (command.isOpen() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        return command.isOpen();
    }

    private static Thread startReader(
            InputStream stream,
            LogCollector output,
            String name) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            stream,
                            StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    if (read > 0) {
                        output.append(new String(buffer, 0, read));
                    }
                }
            } catch (IOException exception) {
                output.append(
                        "[平台] 读取部署输出失败："
                                + exception.getMessage()
                                + "\n");
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static String remoteCommand(
            String remoteDirectory,
            String scriptFilename) {
        return "mkdir -p " + shellQuote(remoteDirectory)
                + " && cd " + shellQuote(remoteDirectory)
                + " && env WORKCOVE_DEPLOY_DIRECTORY=\"$(pwd -P)\""
                + " REGENT_DEPLOY_DIRECTORY=\"$(pwd -P)\""
                + " WORKCOVE_DEPLOY_SCRIPT="
                + shellQuote(scriptFilename)
                + " REGENT_DEPLOY_SCRIPT="
                + shellQuote(scriptFilename)
                + " bash -s";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static final class LogCollector {

        private final StringBuilder content = new StringBuilder();
        private final int maxChars;
        private boolean truncated;

        private LogCollector(int maxChars) {
            this.maxChars = maxChars;
        }

        private synchronized void append(String value) {
            if (value == null || value.isEmpty() || truncated) return;
            int remaining = maxChars - content.length();
            if (remaining <= 0) {
                truncated = true;
                return;
            }
            if (value.length() <= remaining) {
                content.append(value);
                return;
            }
            content.append(value, 0, remaining);
            content.append("\n[平台] 日志过长，后续内容已截断\n");
            truncated = true;
        }

        private synchronized String value() {
            return content.toString();
        }
    }
}
