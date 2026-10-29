package com.pydlove.workcove.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.development.application.CodexAttachmentService;
import com.pydlove.workcove.development.application.DeploymentExecutionService;
import com.pydlove.workcove.development.application.GitRepositoryService;
import com.pydlove.workcove.development.application.ManagedServerService;
import com.pydlove.workcove.development.application.SftpService;
import com.pydlove.workcove.development.application.SogouInputStatsService;
import com.pydlove.workcove.development.application.SshTerminalService;
import com.pydlove.workcove.development.application.TestEnvironmentService;
import com.pydlove.workcove.development.application.UserLocalStateService;
import com.pydlove.workcove.development.application.WorkFileSearchService;
import com.pydlove.workcove.development.application.ZentaoService;
import com.pydlove.workcove.development.application.ZentaoTestEvidenceService;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.CodexAttachmentStore;
import com.pydlove.workcove.development.domain.port.CodexGateway;
import com.pydlove.workcove.development.domain.port.DirectoryPicker;
import com.pydlove.workcove.development.domain.port.GitRepositoryGateway;
import com.pydlove.workcove.development.domain.port.IdeLauncher;
import com.pydlove.workcove.development.domain.port.LocalDirectoryInspector;
import com.pydlove.workcove.development.domain.port.RemoteDeploymentGateway;
import com.pydlove.workcove.development.domain.port.ApplicationHost;
import com.pydlove.workcove.development.domain.port.ServerLauncher;
import com.pydlove.workcove.development.domain.port.SftpGateway;
import com.pydlove.workcove.development.domain.port.SogouInputStatsGateway;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.development.domain.port.ZentaoGateway;
import com.pydlove.workcove.development.domain.port.ZentaoTestEvidenceStore;
import com.pydlove.workcove.development.domain.port.WorkFileSearchGateway;
import com.pydlove.workcove.development.domain.repository.ManagedServerRepository;
import com.pydlove.workcove.development.domain.repository.TestEnvironmentRepository;
import com.pydlove.workcove.development.domain.repository.ZentaoRepository;
import com.pydlove.workcove.development.domain.repository.UserLocalStateRepository;
import com.pydlove.workcove.development.infrastructure.MacDirectoryPicker;
import com.pydlove.workcove.development.infrastructure.FileCodexAttachmentStore;
import com.pydlove.workcove.development.infrastructure.FileSystemDirectoryInspector;
import com.pydlove.workcove.development.infrastructure.FileSystemWorkFileSearchGateway;
import com.pydlove.workcove.development.infrastructure.FileZentaoTestEvidenceStore;
import com.pydlove.workcove.development.infrastructure.MacApplicationHost;
import com.pydlove.workcove.development.infrastructure.MacIdeLauncher;
import com.pydlove.workcove.development.infrastructure.HttpZentaoGateway;
import com.pydlove.workcove.development.infrastructure.MacServerLauncher;
import com.pydlove.workcove.development.infrastructure.MacSogouInputStatsGateway;
import com.pydlove.workcove.development.infrastructure.ProcessCodexGateway;
import com.pydlove.workcove.development.infrastructure.ProcessGitRepositoryGateway;
import com.pydlove.workcove.development.infrastructure.SshjTerminalGateway;
import com.pydlove.workcove.development.infrastructure.SshjSftpGateway;
import com.pydlove.workcove.development.infrastructure.SshjRemoteDeploymentGateway;
import com.pydlove.workcove.development.infrastructure.security.AesGcmCredentialCipher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DevelopmentConfiguration {

    @Bean
    public DirectoryPicker directoryPicker() {
        return new MacDirectoryPicker();
    }

    @Bean
    public LocalDirectoryInspector localDirectoryInspector() {
        return new FileSystemDirectoryInspector();
    }

    @Bean
    public WorkFileSearchGateway workFileSearchGateway() {
        return new FileSystemWorkFileSearchGateway();
    }

    @Bean
    public WorkFileSearchService workFileSearchService(
            WorkFileSearchGateway workFileSearchGateway) {
        return new WorkFileSearchService(workFileSearchGateway);
    }

    @Bean
    public ApplicationHost applicationHost() {
        return new MacApplicationHost();
    }

    @Bean
    public IdeLauncher ideLauncher() {
        return new MacIdeLauncher();
    }

    @Bean
    public GitRepositoryGateway gitRepositoryGateway() {
        return new ProcessGitRepositoryGateway();
    }

    @Bean
    public GitRepositoryService gitRepositoryService(
            GitRepositoryGateway gitRepositoryGateway) {
        return new GitRepositoryService(gitRepositoryGateway);
    }

    @Bean
    public ZentaoGateway zentaoGateway(ObjectMapper objectMapper) {
        return new HttpZentaoGateway(objectMapper);
    }

    @Bean
    public ZentaoService zentaoService(
            ZentaoRepository repository,
            CredentialCipher credentialCipher,
            ZentaoGateway zentaoGateway,
            @Qualifier("clock") java.time.Clock clock) {
        return new ZentaoService(
                repository,
                credentialCipher,
                zentaoGateway,
                clock);
    }

    @Bean
    public ZentaoTestEvidenceStore zentaoTestEvidenceStore(
            @Value("${workcove.zentao-test-evidence-dir}") String directory) {
        return new FileZentaoTestEvidenceStore(
                java.nio.file.Path.of(directory));
    }

    @Bean
    public ZentaoTestEvidenceService zentaoTestEvidenceService(
            ZentaoRepository repository,
            ZentaoTestEvidenceStore evidenceStore) {
        return new ZentaoTestEvidenceService(
                repository,
                evidenceStore);
    }

    @Bean
    public TestEnvironmentService testEnvironmentService(
            TestEnvironmentRepository repository,
            CredentialCipher credentialCipher,
            @Qualifier("clock") java.time.Clock clock) {
        return new TestEnvironmentService(
                repository,
                credentialCipher,
                clock);
    }

    @Bean
    public UserLocalStateService userLocalStateService(
            UserLocalStateRepository repository,
            @Qualifier("clock") java.time.Clock clock) {
        return new UserLocalStateService(repository, clock);
    }

    @Bean
    public CredentialCipher credentialCipher() {
        String masterKey = ConfigEncryption.resolveMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "服务器密码加密需要 WORKCOVE_CONFIG_KEY 或 ~/.workcove/config.key");
        }
        return new AesGcmCredentialCipher(masterKey);
    }

    @Bean
    public ServerLauncher serverLauncher() {
        return new MacServerLauncher();
    }

    @Bean
    public SshTerminalGateway sshTerminalGateway() {
        return new SshjTerminalGateway();
    }

    @Bean
    public SftpGateway sftpGateway() {
        return new SshjSftpGateway();
    }

    @Bean
    public SogouInputStatsGateway sogouInputStatsGateway(
            ObjectMapper objectMapper,
            @Qualifier("clock") java.time.Clock clock) {
        return new MacSogouInputStatsGateway(objectMapper, clock);
    }

    @Bean
    public RemoteDeploymentGateway remoteDeploymentGateway() {
        return new SshjRemoteDeploymentGateway();
    }

    @Bean(destroyMethod = "close")
    public CodexGateway codexGateway(ObjectMapper objectMapper) {
        return new ProcessCodexGateway(objectMapper);
    }

    @Bean
    public CodexAttachmentStore codexAttachmentStore(
            @Value("${workcove.codex.attachments-dir}") String directory) {
        return new FileCodexAttachmentStore(java.nio.file.Path.of(directory));
    }

    @Bean
    public CodexAttachmentService codexAttachmentService(
            CodexAttachmentStore attachmentStore) {
        return new CodexAttachmentService(attachmentStore);
    }

    @Bean
    public ManagedServerService managedServerService(
            ManagedServerRepository repository,
            CredentialCipher credentialCipher,
            ServerLauncher serverLauncher,
            @Qualifier("clock") java.time.Clock clock) {
        return new ManagedServerService(
                repository,
                credentialCipher,
                serverLauncher,
                clock);
    }

    @Bean
    public SshTerminalService sshTerminalService(
            ManagedServerRepository repository,
            CredentialCipher credentialCipher,
            SshTerminalGateway sshTerminalGateway,
            @Qualifier("clock") java.time.Clock clock) {
        return new SshTerminalService(
                repository,
                credentialCipher,
                sshTerminalGateway,
                clock);
    }

    @Bean
    public SftpService sftpService(
            ManagedServerRepository repository,
            CredentialCipher credentialCipher,
            SftpGateway sftpGateway,
            @Qualifier("clock") java.time.Clock clock) {
        return new SftpService(
                repository,
                credentialCipher,
                sftpGateway,
                clock);
    }

    @Bean
    public DeploymentExecutionService deploymentExecutionService(
            ManagedServerRepository repository,
            CredentialCipher credentialCipher,
            RemoteDeploymentGateway remoteDeploymentGateway,
            @Qualifier("clock") java.time.Clock clock) {
        return new DeploymentExecutionService(
                repository,
                credentialCipher,
                remoteDeploymentGateway,
                clock);
    }

    @Bean
    public SogouInputStatsService sogouInputStatsService(
            SogouInputStatsGateway gateway) {
        return new SogouInputStatsService(gateway);
    }
}
