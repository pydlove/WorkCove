package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.application.command.ZentaoConnectionCommand;
import com.pydlove.workcove.development.application.command.ZentaoResolveCommand;
import com.pydlove.workcove.development.application.query.ZentaoBugView;
import com.pydlove.workcove.development.application.query.ZentaoConnectionView;
import com.pydlove.workcove.development.application.query.ZentaoMediaView;
import com.pydlove.workcove.development.application.query.ZentaoProjectView;
import com.pydlove.workcove.development.application.query.ZentaoSyncResult;
import com.pydlove.workcove.development.domain.model.ZentaoBug;
import com.pydlove.workcove.development.domain.model.ZentaoBaseUrl;
import com.pydlove.workcove.development.domain.model.ZentaoConnection;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.ZentaoGateway;
import com.pydlove.workcove.development.domain.repository.ZentaoRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ZentaoService {

    private static final Set<String> RESOLUTIONS = Set.of(
            "fixed",
            "notrepro",
            "bydesign",
            "duplicate",
            "external",
            "postponed",
            "willnotfix",
            "tostory");

    private final ZentaoRepository repository;
    private final CredentialCipher credentialCipher;
    private final ZentaoGateway gateway;
    private final Clock clock;

    public ZentaoService(
            ZentaoRepository repository,
            CredentialCipher credentialCipher,
            ZentaoGateway gateway,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.credentialCipher = Objects.requireNonNull(credentialCipher);
        this.gateway = Objects.requireNonNull(gateway);
        this.clock = Objects.requireNonNull(clock);
    }

    public ZentaoConnectionView connection(long ownerId, String workspaceId) {
        return repository.findConnection(ownerId, workspaceId)
                .map(connection -> ZentaoConnectionView.from(
                        connection,
                        credentialCipher.decrypt(
                                connection.passwordCiphertext())))
                .orElseGet(ZentaoConnectionView::empty);
    }

    public ZentaoConnectionView saveConnection(
            long ownerId,
            String workspaceId,
            ZentaoConnectionCommand command) {
        String baseUrl = normalizeBaseUrl(command.baseUrl());
        String username = required(command.username(), "请输入禅道用户名", 128);
        List<String> projectIds = normalizedProjectIds(command.projectIds());
        if (projectIds.isEmpty()) {
            throw new DomainException("请至少选择一个需要同步的禅道项目");
        }
        ZentaoConnection existing = repository
                .findConnection(ownerId, workspaceId)
                .orElse(null);
        String password = resolvePassword(
                existing,
                baseUrl,
                username,
                command.password());

        String token = gateway.authenticate(baseUrl, username, password);
        ensureProjectsExist(baseUrl, token, projectIds);
        if (existing != null && !baseUrl.equals(existing.baseUrl())) {
            repository.deleteBugs(ownerId, workspaceId);
        }
        Instant now = clock.instant();
        ZentaoConnection saved = repository.saveConnection(
                new ZentaoConnection(
                        existing == null ? null : existing.id(),
                        ownerId,
                        workspaceId,
                        baseUrl,
                        username,
                        projectIds,
                        credentialCipher.encrypt(password),
                        existing == null ? null : existing.lastSyncedAt(),
                        existing == null ? now : existing.createdAt(),
                        now));
        return ZentaoConnectionView.from(saved, password);
    }

    public List<ZentaoProjectView> availableProjects(
            long ownerId,
            String workspaceId,
            ZentaoConnectionCommand command) {
        String baseUrl = normalizeBaseUrl(command.baseUrl());
        String username = required(command.username(), "请输入禅道用户名", 128);
        ZentaoConnection existing = repository
                .findConnection(ownerId, workspaceId)
                .orElse(null);
        String password = resolvePassword(
                existing,
                baseUrl,
                username,
                command.password());
        String token = gateway.authenticate(baseUrl, username, password);
        return gateway.listProjects(baseUrl, token).stream()
                .filter(this::isActiveProject)
                .map(ZentaoProjectView::from)
                .toList();
    }

    public List<ZentaoBugView> bugs(long ownerId, String workspaceId) {
        ZentaoConnection connection = repository
                .findConnection(ownerId, workspaceId)
                .orElse(null);
        if (connection == null
                || connection.username().isBlank()
                || connection.projectIds().isEmpty()) {
            return List.of();
        }
        Set<String> projectIds = Set.copyOf(connection.projectIds());
        return repository.findBugs(ownerId, workspaceId).stream()
                .filter(bug -> bug.active()
                        || "resolved".equalsIgnoreCase(bug.localStatus()))
                .filter(bug -> projectIds.contains(bug.projectId()))
                .filter(bug -> connection.username().equalsIgnoreCase(
                        bug.assignedTo()))
                .map(ZentaoBugView::from)
                .toList();
    }

    public ZentaoSyncResult sync(long ownerId, String workspaceId) {
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        if (connection.projectIds().isEmpty()) {
            throw new DomainException("请先选择需要同步的禅道项目");
        }
        String token = gateway.authenticate(
                connection.baseUrl(),
                connection.username(),
                credentialCipher.decrypt(connection.passwordCiphertext()));
        Map<String, ZentaoGateway.Project> availableProjects =
                new LinkedHashMap<>();
        gateway.listProjects(connection.baseUrl(), token)
                .stream()
                .filter(this::isActiveProject)
                .forEach(project -> availableProjects.put(
                        project.id(),
                        project));
        List<ZentaoGateway.Project> selectedProjects = connection.projectIds()
                .stream()
                .map(availableProjects::get)
                .filter(Objects::nonNull)
                .toList();
        if (selectedProjects.isEmpty()) {
            throw new DomainException("所选禅道项目不存在、已关闭或无权访问");
        }
        Map<String, String> productNames = new LinkedHashMap<>();
        gateway.listProducts(connection.baseUrl(), token)
                .forEach(product -> productNames.put(
                        product.id(),
                        product.name()));
        List<ProjectBugs> loaded = new ArrayList<>();
        for (ZentaoGateway.Project project : selectedProjects) {
            loaded.add(new ProjectBugs(
                    project,
                    gateway.listProjectBugs(
                            connection.baseUrl(),
                            token,
                            project.id(),
                            connection.username())));
        }

        Instant now = clock.instant();
        repository.markBugsInactive(ownerId, workspaceId);
        int bugCount = 0;
        for (ProjectBugs projectBugs : loaded) {
            for (ZentaoGateway.RemoteBug remote : projectBugs.bugs()) {
                ZentaoBug existing = repository.findBugByRemote(
                        ownerId,
                        workspaceId,
                        remote.productId(),
                        remote.id()).orElse(null);
                if (existing != null && existing.localDeleted()) {
                    continue;
                }
                boolean locallyResolved =
                        existing != null && existing.localResolved();
                String localStatus = existing == null
                        ? "synced"
                        : existing.localStatus();
                boolean reopened = false;
                if (!locallyResolved
                        && "resolved".equals(localStatus)
                        && !"resolved".equalsIgnoreCase(remote.status())
                        && !"closed".equalsIgnoreCase(remote.status())) {
                    localStatus = "synced";
                    reopened = true;
                }
                boolean remotelyResolved =
                        "resolved".equalsIgnoreCase(remote.status())
                                || "closed".equalsIgnoreCase(remote.status());
                if (remotelyResolved || locallyResolved) {
                    localStatus = "resolved";
                }
                repository.saveBug(new ZentaoBug(
                        existing == null ? null : existing.id(),
                        ownerId,
                        workspaceId,
                        remote.id(),
                        projectBugs.project().id(),
                        projectBugs.project().name(),
                        remote.productId(),
                        productNames.getOrDefault(
                                remote.productId(),
                                "产品 " + remote.productId()),
                        remote.title(),
                        remote.severity(),
                        remote.priority(),
                        remote.status(),
                        remote.steps(),
                        remote.openedBy(),
                        remote.openedDate(),
                        remote.assignedTo(),
                        !locallyResolved && !remotelyResolved,
                        false,
                        locallyResolved,
                        localStatus,
                        existing == null ? "" : existing.resolution(),
                        existing == null ? null : existing.solutionText(),
                        existing == null ? null : existing.reasonText(),
                        reopened || existing == null
                                ? null
                                : existing.pushedAt(),
                        now,
                        existing == null ? now : existing.createdAt(),
                        now));
                bugCount++;
            }
        }
        repository.saveConnection(new ZentaoConnection(
                connection.id(),
                connection.ownerId(),
                connection.workspaceId(),
                connection.baseUrl(),
                connection.username(),
                connection.projectIds(),
                connection.passwordCiphertext(),
                now,
                connection.createdAt(),
                now));
        return new ZentaoSyncResult(selectedProjects.size(), bugCount, now);
    }

    public ZentaoBugView resolve(
            long ownerId,
            String workspaceId,
            long bugId,
            ZentaoResolveCommand command) {
        ZentaoBug bug = repository.findBug(ownerId, workspaceId, bugId)
                .orElseThrow(() -> new DomainException("Bug 不存在或无权访问"));
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        String resolution = command.resolution() == null
                ? "fixed"
                : command.resolution().trim().toLowerCase(Locale.ROOT);
        if (!RESOLUTIONS.contains(resolution)) {
            throw new DomainException("不支持的解决方案");
        }
        String solution = required(
                command.solution(),
                "请填写解决方案",
                4000);
        String reason = required(command.reason(), "请填写原因", 4000);
        String comment = "解决方案：\n" + solution
                + "\n\n原因：\n" + reason
                + "\n\n由 8小时工作台回写。";

        String token = gateway.authenticate(
                connection.baseUrl(),
                connection.username(),
                credentialCipher.decrypt(connection.passwordCiphertext()));
        String assignedTo = bug.assignedTo().isBlank()
                ? connection.username()
                : bug.assignedTo();
        gateway.resolveBug(
                connection.baseUrl(),
                token,
                bug.remoteId(),
                assignedTo,
                resolution,
                comment);

        Instant now = clock.instant();
        ZentaoBug updated = repository.saveBug(new ZentaoBug(
                bug.id(),
                bug.ownerId(),
                bug.workspaceId(),
                bug.remoteId(),
                bug.projectId(),
                bug.projectName(),
                bug.productId(),
                bug.productName(),
                bug.title(),
                bug.severity(),
                bug.priority(),
                "resolved",
                bug.steps(),
                bug.openedBy(),
                bug.openedDate(),
                bug.assignedTo(),
                false,
                bug.localDeleted(),
                bug.localResolved(),
                "resolved",
                resolution,
                solution,
                reason,
                now,
                bug.lastSyncedAt(),
                bug.createdAt(),
                now));
        return ZentaoBugView.from(updated);
    }

    public ZentaoBugView resolveLocally(
            long ownerId,
            String workspaceId,
            long bugId,
            String rawSolution,
            String rawReason) {
        ZentaoBug bug = repository.findBug(ownerId, workspaceId, bugId)
                .orElseThrow(() -> new DomainException(
                        "Bug 不存在或无权访问"));
        if (bug.localDeleted()) {
            throw new DomainException("Bug 已从 8小时工作台移除");
        }
        String solution = required(
                rawSolution,
                "请填写解决方案",
                4000);
        String reason = required(
                rawReason,
                "请填写定位原因",
                4000);
        repository.markBugResolvedLocally(
                ownerId,
                workspaceId,
                bugId,
                "fixed",
                solution,
                reason);
        return repository.findBug(ownerId, workspaceId, bugId)
                .map(ZentaoBugView::from)
                .orElseThrow(() -> new DomainException(
                        "Bug 不存在或无权访问"));
    }

    public ZentaoMediaView media(
            long ownerId,
            String workspaceId,
            long bugId,
            String source) {
        ZentaoBug bug = repository.findBug(ownerId, workspaceId, bugId)
                .orElseThrow(() -> new DomainException(
                        "Bug 不存在或无权访问"));
        if (bug.localDeleted()) {
            throw new DomainException("Bug 已从 8小时工作台移除");
        }
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        String token = gateway.authenticate(
                connection.baseUrl(),
                connection.username(),
                credentialCipher.decrypt(connection.passwordCiphertext()));
        ZentaoGateway.MediaFile media = gateway.fetchMedia(
                connection.baseUrl(),
                token,
                source);
        return new ZentaoMediaView(
                media.content(),
                media.contentType());
    }

    public void deleteBug(
            long ownerId,
            String workspaceId,
            long bugId) {
        repository.findBug(ownerId, workspaceId, bugId)
                .orElseThrow(() -> new DomainException("Bug 不存在或无权访问"));
        repository.deleteBugLocally(ownerId, workspaceId, bugId);
    }

    private ZentaoConnection requireConnection(long ownerId, String workspaceId) {
        return repository.findConnection(ownerId, workspaceId)
                .orElseThrow(() -> new DomainException("请先配置禅道连接"));
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        return ZentaoBaseUrl.normalize(rawBaseUrl);
    }

    private String resolvePassword(
            ZentaoConnection existing,
            String baseUrl,
            String username,
            String suppliedPassword) {
        String password = suppliedPassword;
        if ((password == null || password.isBlank())
                && existing != null
                && baseUrl.equals(existing.baseUrl())
                && username.equalsIgnoreCase(existing.username())) {
            password = credentialCipher.decrypt(
                    existing.passwordCiphertext());
        }
        if (password == null || password.isBlank()) {
            throw new DomainException("请输入禅道密码");
        }
        if (password.length() > 1024) {
            throw new DomainException("禅道密码过长");
        }
        return password;
    }

    private void ensureProjectsExist(
            String baseUrl,
            String token,
            List<String> projectIds) {
        Set<String> available = gateway.listProjects(baseUrl, token).stream()
                .filter(this::isActiveProject)
                .map(ZentaoGateway.Project::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!available.containsAll(projectIds)) {
            throw new DomainException("所选禅道项目不存在、已关闭或无权访问");
        }
    }

    private boolean isActiveProject(ZentaoGateway.Project project) {
        return project != null
                && !"closed".equalsIgnoreCase(project.status());
    }

    private List<String> normalizedProjectIds(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> projectIds = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .map(value -> {
                    if (!value.matches("[a-zA-Z0-9_-]{1,64}")) {
                        throw new DomainException("禅道项目 ID 无效");
                    }
                    return value;
                })
                .toList();
        if (projectIds.size() > 50) {
            throw new DomainException("一次最多选择 50 个禅道项目");
        }
        return projectIds;
    }

    private String required(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new DomainException(message);
        if (normalized.length() > maxLength) {
            throw new DomainException("输入内容过长");
        }
        return normalized;
    }

    private record ProjectBugs(
            ZentaoGateway.Project project,
            List<ZentaoGateway.RemoteBug> bugs
    ) {
    }
}
