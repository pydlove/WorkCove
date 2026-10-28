package com.pydlove.workcove.identity.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkCoveMailTlsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void prefersTheConfiguredTrustStore() throws Exception {
        Path configured = Files.createFile(
                tempDirectory.resolve("configured.p12"));
        Path shared = Files.createDirectories(
                        tempDirectory.resolve(".workcove"))
                .resolve("mail-truststore.p12");
        Files.createFile(shared);

        assertThat(WorkCoveMailTls.resolveTrustStore(
                configured.toString(),
                tempDirectory.toString()))
                .isEqualTo(configured);
    }

    @Test
    void fallsBackToTheSharedUserTrustStore() throws Exception {
        Path shared = Files.createDirectories(
                        tempDirectory.resolve(".workcove"))
                .resolve("mail-truststore.p12");
        Files.createFile(shared);

        assertThat(WorkCoveMailTls.resolveTrustStore(
                tempDirectory.resolve("missing.p12").toString(),
                tempDirectory.toString()))
                .isEqualTo(shared);
    }
}
