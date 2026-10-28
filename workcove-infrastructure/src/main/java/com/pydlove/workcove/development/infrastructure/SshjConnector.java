package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.shared.domain.DomainException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Buffer;
import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;

final class SshjConnector {

    private static final Logger log = LoggerFactory.getLogger(SshjConnector.class);

    ConnectedClient connect(
            SshTerminalGateway.Connection connection,
            SshTerminalGateway.HostKey expectedHostKey) {
        CapturingHostKeyVerifier verifier =
                new CapturingHostKeyVerifier(expectedHostKey);
        SSHClient client = new SSHClient();
        client.setConnectTimeout(10_000);
        client.setTimeout(0);
        client.addHostKeyVerifier(verifier);

        try {
            client.connect(connection.host(), connection.port());
            client.authPassword(connection.username(), connection.password());
            return new ConnectedClient(
                    client,
                    observedHostKey(verifier.observed(), expectedHostKey));
        } catch (IOException exception) {
            closeQuietly(client);
            if (verifier.mismatch()) {
                throw new DomainException(
                        "SSH 主机密钥不匹配，连接已中止。服务器或网络可能发生变化");
            }
            log.warn("SSH connection failed for {}:{}: {}",
                    connection.host(),
                    connection.port(),
                    exception.getMessage());
            throw new DomainException("SSH 连接失败，请检查主机、端口、用户名和密码");
        }
    }

    private static SshTerminalGateway.HostKey observedHostKey(
            PublicKey publicKey,
            SshTerminalGateway.HostKey expectedHostKey) {
        if (publicKey == null) {
            if (expectedHostKey != null) return expectedHostKey;
            throw new DomainException("无法读取 SSH 主机密钥");
        }
        try {
            KeyType keyType = KeyType.fromKey(publicKey);
            if (keyType == KeyType.UNKNOWN) {
                throw new DomainException("不支持的 SSH 主机密钥类型");
            }
            byte[] keyBlob = sshPublicKeyBlob(keyType, publicKey);
            String digest = Base64.getEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(keyBlob));
            return new SshTerminalGateway.HostKey(
                    keyType.toString(),
                    "SHA256:" + digest,
                    Base64.getEncoder().encodeToString(keyBlob));
        } catch (Exception exception) {
            throw new DomainException("无法计算 SSH 主机密钥指纹");
        }
    }

    private static byte[] sshPublicKeyBlob(
            KeyType keyType,
            PublicKey publicKey) {
        Buffer.PlainBuffer buffer = new Buffer.PlainBuffer();
        keyType.putPubKeyIntoBuffer(publicKey, buffer);
        return buffer.getCompactData();
    }

    static void closeQuietly(SSHClient client) {
        try {
            client.close();
        } catch (IOException ignored) {
        }
    }

    record ConnectedClient(
            SSHClient client,
            SshTerminalGateway.HostKey hostKey) {
    }

    private static final class CapturingHostKeyVerifier
            implements HostKeyVerifier {

        private final byte[] expected;
        private PublicKey observed;
        private boolean mismatch;

        private CapturingHostKeyVerifier(
                SshTerminalGateway.HostKey expectedHostKey) {
            this.expected = expectedHostKey == null
                    ? null
                    : Base64.getDecoder().decode(expectedHostKey.publicKey());
        }

        @Override
        public boolean verify(String hostname, int port, PublicKey key) {
            observed = key;
            if (expected == null) return true;
            try {
                byte[] standardBlob = sshPublicKeyBlob(
                        KeyType.fromKey(key),
                        key);
                boolean matches = MessageDigest.isEqual(expected, standardBlob)
                        || legacyEncodedKeyMatches(key);
                mismatch = !matches;
                return matches;
            } catch (Exception exception) {
                mismatch = true;
                return false;
            }
        }

        @Override
        public List<String> findExistingAlgorithms(String hostname, int port) {
            return List.of();
        }

        private PublicKey observed() {
            return observed;
        }

        private boolean mismatch() {
            return mismatch;
        }

        private boolean legacyEncodedKeyMatches(PublicKey key) {
            byte[] encoded = key.getEncoded();
            return encoded != null
                    && MessageDigest.isEqual(expected, encoded);
        }
    }
}
