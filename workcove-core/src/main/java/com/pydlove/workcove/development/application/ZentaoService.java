package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.application.command.ZentaoConnectionCommand;
import com.pydlove.workcove.development.application.command.ZentaoBuildCreateCommand;
import com.pydlove.workcove.development.application.command.ZentaoResolveCommand;
import com.pydlove.workcove.development.application.command.ZentaoTestAttachment;
import com.pydlove.workcove.development.application.command.ZentaoTestTaskCreateCommand;
import com.pydlove.workcove.development.application.query.ZentaoBugView;
import com.pydlove.workcove.development.application.query.ZentaoBuildOptionsView;
import com.pydlove.workcove.development.application.query.ZentaoBuildView;
import com.pydlove.workcove.development.application.query.ZentaoConnectionView;
import com.pydlove.workcove.development.application.query.ZentaoCreatedBuildView;
import com.pydlove.workcove.development.application.query.ZentaoCreatedTestTaskView;
import com.pydlove.workcove.development.application.query.ZentaoMediaView;
import com.pydlove.workcove.development.application.query.ZentaoProjectView;
import com.pydlove.workcove.development.application.query.ZentaoSyncResult;
import com.pydlove.workcove.development.application.query.ZentaoSystemView;
import com.pydlove.workcove.development.application.query.ZentaoTestTaskOptionsView;
import com.pydlove.workcove.development.domain.model.ZentaoBug;
import com.pydlove.workcove.development.domain.model.ZentaoBuild;
import com.pydlove.workcove.development.domain.model.ZentaoBaseUrl;
import com.pydlove.workcove.development.domain.model.ZentaoConnection;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.ZentaoGateway;
import com.pydlove.workcove.development.domain.repository.ZentaoRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final Set<String> TEST_TASK_TYPES = Set.of(
            "integrate",
            "system",
            "acceptance",
            "performance",
            "safety");
    private static final Set<String> TEST_TASK_STATUSES = Set.of(
            "wait",
            "doing",
            "done",
            "blocked");
    private static final int MAX_TEST_ATTACHMENTS = 20;
    private static final int MAX_TEST_ATTACHMENT_BYTES = 10 * 1024 * 1024;

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
            repository.deleteBuilds(ownerId, workspaceId);
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

    public List<ZentaoBuildView> builds(long ownerId, String workspaceId) {
        ZentaoConnection connection = repository
                .findConnection(ownerId, workspaceId)
                .orElse(null);
        if (connection == null || connection.projectIds().isEmpty()) {
            return List.of();
        }
        Set<String> projectIds = Set.copyOf(connection.projectIds());
        return repository.findBuilds(ownerId, workspaceId).stream()
                .filter(build -> projectIds.contains(build.projectId()))
                .map(ZentaoBuildView::from)
                .toList();
    }

    public ZentaoBuildOptionsView buildOptions(
            long ownerId,
            String workspaceId) {
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        if (connection.projectIds().isEmpty()) {
            throw new DomainException("请先选择需要同步的禅道项目");
        }
        String token = authenticate(connection);
        Map<String, String> projectNames = new LinkedHashMap<>();
        gateway.listProjects(connection.baseUrl(), token)
                .stream()
                .filter(project -> connection.projectIds().contains(
                        project.id()))
                .forEach(project -> projectNames.put(
                        project.id(),
                        project.name()));
        List<ZentaoBuildOptionsView.Execution> executions =
                new ArrayList<>();
        for (String projectId : connection.projectIds()) {
            for (ZentaoGateway.RemoteExecution execution
                    : gateway.listProjectExecutions(
                    connection.baseUrl(),
                    token,
                    projectId)) {
                executions.add(ZentaoBuildOptionsView.execution(
                        execution,
                        projectNames.getOrDefault(
                                execution.projectId(),
                                projectNames.getOrDefault(
                                        projectId,
                                        "项目 " + projectId))));
            }
        }
        Map<String, ZentaoBuildOptionsView.Product> products =
                new LinkedHashMap<>();
        gateway.listProducts(connection.baseUrl(), token)
                .stream()
                .map(ZentaoBuildOptionsView::product)
                .forEach(product -> products.put(product.id(), product));
        for (ZentaoBuildOptionsView.Execution execution : executions) {
            if (execution.productId().isBlank()
                    || products.containsKey(execution.productId())) {
                continue;
            }
            ZentaoGateway.Product product = gateway.getProduct(
                    connection.baseUrl(),
                    token,
                    execution.productId());
            products.put(
                    product.id(),
                    ZentaoBuildOptionsView.product(product));
        }
        List<ZentaoBuildOptionsView.User> users = gateway
                .listUsers(connection.baseUrl(), token)
                .stream()
                .map(ZentaoBuildOptionsView::user)
                .toList();
        return new ZentaoBuildOptionsView(
                executions,
                new ArrayList<>(products.values()),
                users);
    }

    public List<ZentaoSystemView> systems(
            long ownerId,
            String workspaceId,
            String productId) {
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        String normalizedProductId = requiredId(
                productId,
                "禅道产品 ID 无效");
        String token = authenticate(connection);
        return gateway.listProductSystems(
                        connection.baseUrl(),
                        token,
                        normalizedProductId)
                .stream()
                .map(ZentaoSystemView::from)
                .toList();
    }

    public ZentaoTestTaskOptionsView testTaskOptions(
            long ownerId,
            String workspaceId,
            String productId) {
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        String normalizedProductId = requiredId(
                productId,
                "禅道产品 ID 无效");
        String token = authenticate(connection);
        return new ZentaoTestTaskOptionsView(
                gateway.listUsers(connection.baseUrl(), token)
                        .stream()
                        .map(ZentaoBuildOptionsView::user)
                        .toList(),
                gateway.listProductTestReports(
                                connection.baseUrl(),
                                token,
                                normalizedProductId)
                        .stream()
                        .map(ZentaoTestTaskOptionsView::report)
                        .toList());
    }

    public ZentaoCreatedBuildView createBuild(
            long ownerId,
            String workspaceId,
            ZentaoBuildCreateCommand command) {
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        String projectId = requiredId(
                command.projectId(),
                "请选择所属迭代");
        if (!connection.projectIds().contains(projectId)) {
            throw new DomainException("所属迭代不在当前同步项目中");
        }
        String executionId = requiredId(
                command.executionId(),
                "请选择所属迭代");
        String productId = requiredId(
                command.productId(),
                "请选择所属产品");
        String systemId = requiredId(
                command.systemId(),
                "请选择所属应用");
        String name = required(
                command.name(),
                "请输入构建名称",
                500);
        String builder = required(
                command.builder(),
                "请选择构建者",
                128);
        String date = requiredDate(
                command.date(),
                "请输入打包日期");
        String downloadUrl = optional(
                command.downloadUrl(),
                4096);
        String token = authenticate(connection);
        ensureBuildSelection(
                connection,
                token,
                projectId,
                executionId,
                productId,
                systemId);
        ZentaoGateway.CreatedBuild created = gateway.createBuild(
                connection.baseUrl(),
                token,
                new ZentaoGateway.CreateBuild(
                        projectId,
                        executionId,
                        productId,
                        systemId,
                        name,
                        builder,
                        date,
                        downloadUrl));
        return new ZentaoCreatedBuildView(created.id(), created.name());
    }

    public ZentaoCreatedTestTaskView createTestTask(
            long ownerId,
            String workspaceId,
            long buildId,
            ZentaoTestTaskCreateCommand command) {
        ZentaoBuild build = repository.findBuilds(ownerId, workspaceId)
                .stream()
                .filter(item -> item.id() != null && item.id() == buildId)
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "构建不存在或无权访问"));
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        if (!connection.projectIds().contains(build.projectId())) {
            throw new DomainException("构建所属项目不在当前同步项目中");
        }
        if (build.productId().isBlank()
                || build.executionId().isBlank()
                || build.remoteId().isBlank()) {
            throw new DomainException(
                    "构建缺少产品、迭代或版本信息，请先重新同步禅道");
        }
        String name = required(
                command.name(),
                "请输入测试单名称",
                500);
        String begin = requiredDate(
                command.begin(),
                "请输入开始日期");
        String end = requiredDate(
                command.end(),
                "请输入结束日期");
        if (end.compareTo(begin) < 0) {
            throw new DomainException("结束日期不能早于开始日期");
        }
        String status = command.status() == null
                || command.status().isBlank()
                ? "wait"
                : command.status().trim().toLowerCase(Locale.ROOT);
        if (!TEST_TASK_STATUSES.contains(status)) {
            throw new DomainException("不支持测试单状态");
        }
        List<String> types = normalizedTestTypes(command.types());
        String owner = optional(command.owner(), 128);
        String members = normalizedMembers(command.members());
        String report = optionalId(
                command.testReport(),
                "测试报告 ID 无效");
        String token = authenticate(connection);
        ZentaoGateway.CreatedTestTask created = gateway.createTestTask(
                connection.baseUrl(),
                token,
                new ZentaoGateway.CreateTestTask(
                        build.projectId(),
                        build.executionId(),
                        build.productId(),
                        build.remoteId(),
                        types,
                        owner,
                        members,
                        status,
                        begin,
                        end,
                        report,
                        name));
        return new ZentaoCreatedTestTaskView(created.id(), created.name());
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
        Map<String, List<ZentaoGateway.RemoteBuild>> loadedBuilds =
                new LinkedHashMap<>();
        for (ZentaoGateway.Project project : selectedProjects) {
            loaded.add(new ProjectBugs(
                    project,
                    gateway.listProjectBugs(
                            connection.baseUrl(),
                            token,
                            project.id(),
                            connection.username())));
            loadedBuilds.put(
                    project.id(),
                    gateway.listProjectBuilds(
                            connection.baseUrl(),
                            token,
                            project.id()));
        }
        Set<String> missingProductIds = new LinkedHashSet<>();
        loaded.stream()
                .flatMap(projectBugs -> projectBugs.bugs().stream())
                .map(ZentaoGateway.RemoteBug::productId)
                .filter(productId -> !productId.isBlank()
                        && !productNames.containsKey(productId))
                .forEach(missingProductIds::add);
        loadedBuilds.values()
                .stream()
                .flatMap(List::stream)
                .map(ZentaoGateway.RemoteBuild::productId)
                .filter(productId -> !productId.isBlank()
                        && !productNames.containsKey(productId))
                .forEach(missingProductIds::add);
        for (String productId : missingProductIds) {
            try {
                ZentaoGateway.Product product = gateway.getProduct(
                        connection.baseUrl(),
                        token,
                        productId);
                productNames.put(product.id(), product.name());
            } catch (DomainException ignored) {
                // Keep the synced record even if a secondary product name is unavailable.
            }
        }

        Instant now = clock.instant();
        repository.markBugsInactive(ownerId, workspaceId);
        repository.deleteBuilds(ownerId, workspaceId);
        int bugCount = 0;
        int buildCount = 0;
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
                        remote.confirmed(),
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
            for (ZentaoGateway.RemoteBuild remote : loadedBuilds.getOrDefault(
                    projectBugs.project().id(),
                    List.of())) {
                ZentaoBuild existing = repository.findBuildByRemote(
                        ownerId,
                        workspaceId,
                        projectBugs.project().id(),
                        remote.id()).orElse(null);
                String productName = remote.productId().isBlank()
                        ? ""
                        : productNames.getOrDefault(
                                remote.productId(),
                                remote.productId());
                repository.saveBuild(new ZentaoBuild(
                        existing == null ? null : existing.id(),
                        ownerId,
                        workspaceId,
                        remote.id(),
                        projectBugs.project().id(),
                        projectBugs.project().name(),
                        remote.productId(),
                        productName,
                        remote.executionId(),
                        remote.name(),
                        remote.status(),
                        remote.builder(),
                        remote.buildDate(),
                        remote.description(),
                        remote.scmRevision(),
                        now,
                        existing == null ? now : existing.createdAt(),
                        now));
                buildCount++;
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
        return new ZentaoSyncResult(
                selectedProjects.size(),
                bugCount,
                buildCount,
                now);
    }

    public ZentaoBugView confirm(
            long ownerId,
            String workspaceId,
            long bugId) {
        ZentaoBug bug = repository.findBug(ownerId, workspaceId, bugId)
                .orElseThrow(() -> new DomainException(
                        "Bug 不存在或无权访问"));
        if (bug.localDeleted()) {
            throw new DomainException("Bug 已从 8小时工作台移除");
        }
        if (bug.confirmed()) {
            return ZentaoBugView.from(bug);
        }
        if (!"active".equalsIgnoreCase(bug.status())) {
            throw new DomainException("只有激活状态的 Bug 可以确认");
        }
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        String token = gateway.authenticate(
                connection.baseUrl(),
                connection.username(),
                credentialCipher.decrypt(connection.passwordCiphertext()));
        String assignedTo = bug.assignedTo().isBlank()
                ? connection.username()
                : bug.assignedTo();
        gateway.confirmBug(
                connection.baseUrl(),
                token,
                bug.remoteId(),
                assignedTo,
                "由 8小时工作台确认。");

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
                bug.status(),
                true,
                bug.steps(),
                bug.openedBy(),
                bug.openedDate(),
                bug.assignedTo(),
                bug.active(),
                bug.localDeleted(),
                bug.localResolved(),
                bug.localStatus(),
                bug.resolution(),
                bug.solutionText(),
                bug.reasonText(),
                bug.pushedAt(),
                bug.lastSyncedAt(),
                bug.createdAt(),
                now));
        return ZentaoBugView.from(updated);
    }

    public boolean commentTestResult(
            long ownerId,
            String workspaceId,
            long bugId,
            String rawRunId,
            String rawEnvironment,
            String rawProof) {
        return commentTestResult(
                ownerId,
                workspaceId,
                bugId,
                rawRunId,
                rawEnvironment,
                rawProof,
                List.of());
    }

    public boolean commentTestResult(
            long ownerId,
            String workspaceId,
            long bugId,
            String rawRunId,
            String rawEnvironment,
            String rawProof,
            List<ZentaoTestAttachment> screenshots) {
        ZentaoBug bug = repository.findBug(ownerId, workspaceId, bugId)
                .orElseThrow(() -> new DomainException(
                        "Bug 不存在或无权访问"));
        if (bug.localDeleted()) {
            throw new DomainException("Bug 已从 8小时工作台移除");
        }
        String runId = required(
                rawRunId,
                "缺少 Codex 测试批次",
                128);
        if (!runId.matches("[a-zA-Z0-9._:-]+")) {
            throw new DomainException("Codex 测试批次无效");
        }
        String environment = required(
                rawEnvironment,
                "请填写测试环境",
                2000);
        String proof = required(
                rawProof,
                "请填写测试说明",
                8000);
        List<ZentaoTestAttachment> attachments = screenshots == null
                ? List.of()
                : List.copyOf(screenshots);
        if (attachments.size() > MAX_TEST_ATTACHMENTS) {
            throw new DomainException(
                    "一次最多上传 " + MAX_TEST_ATTACHMENTS + " 张测试截图");
        }
        for (ZentaoTestAttachment attachment : attachments) {
            if (attachment.content() == null
                    || attachment.content().length == 0) {
                throw new DomainException("测试截图内容为空");
            }
            if (attachment.content().length > MAX_TEST_ATTACHMENT_BYTES) {
                throw new DomainException("单张测试截图不能超过 10MB");
            }
        }
        String marker = "[WorkCove-Codex-Test:" + runId + "]";
        String testedAt = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(clock.instant());
        ZentaoConnection connection = requireConnection(
                ownerId,
                workspaceId);
        String token = gateway.authenticate(
                connection.baseUrl(),
                connection.username(),
                credentialCipher.decrypt(connection.passwordCiphertext()));
        if (gateway.hasBugComment(
                connection.baseUrl(),
                token,
                bug.remoteId(),
                marker)) {
            return true;
        }
        List<ZentaoGateway.UploadedBugFile> uploaded =
                attachments.isEmpty()
                        ? List.of()
                        : gateway.uploadBugFiles(
                                connection.baseUrl(),
                                token,
                                bug.remoteId(),
                                attachments.stream()
                                        .map(attachment ->
                                                new ZentaoGateway.UploadFile(
                                                        attachment.filename(),
                                                        attachment.contentType(),
                                                        attachment.content()))
                                        .toList());
        String screenshotText = uploaded.isEmpty()
                ? "验证截图：无"
                : "验证截图：\n" + uploaded.stream()
                        .map(file -> "- " + file.filename()
                                + (file.url().isBlank()
                                        ? ""
                                        : "：" + file.url()))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("");
        String screenshotImages = uploaded.stream()
                .filter(file -> !file.url().isBlank())
                .map(file -> "<p><img src=\""
                        + escapeHtml(file.url())
                        + "\" alt=\""
                        + escapeHtml(file.filename())
                        + "\"></p>")
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String comment = "【Codex 自动化测试结果】\n"
                + "Bug：#" + bug.remoteId() + " " + bug.title() + "\n"
                + "测试结论：通过\n"
                + "测试环境：" + environment + "\n"
                + "测试时间：" + testedAt + "\n"
                + "测试说明：\n" + proof + "\n"
                + screenshotText + "\n"
                + (screenshotImages.isBlank()
                        ? ""
                        : screenshotImages + "\n")
                + "\n"
                + marker + "\n"
                + "由 8小时工作台自动回写。";
        return gateway.commentBug(
                connection.baseUrl(),
                token,
                bug.remoteId(),
                comment,
                marker);
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
                true,
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

    private String authenticate(ZentaoConnection connection) {
        return gateway.authenticate(
                connection.baseUrl(),
                connection.username(),
                credentialCipher.decrypt(connection.passwordCiphertext()));
    }

    private void ensureBuildSelection(
            ZentaoConnection connection,
            String token,
            String projectId,
            String executionId,
            String productId,
            String systemId) {
        ZentaoGateway.RemoteExecution selectedExecution =
                gateway.listProjectExecutions(
                        connection.baseUrl(),
                        token,
                        projectId)
                .stream()
                .filter(item -> executionId.equals(item.id()))
                .findFirst()
                .orElse(null);
        if (selectedExecution == null) {
            throw new DomainException("所属迭代不存在或无权访问");
        }
        if (!selectedExecution.productId().isBlank()
                && !productId.equals(selectedExecution.productId())) {
            throw new DomainException("所属产品与迭代不匹配");
        }
        boolean productFound = gateway.getProduct(
                        connection.baseUrl(),
                        token,
                        productId)
                .id()
                .equals(productId);
        if (!productFound) {
            throw new DomainException("所属产品不存在或无权访问");
        }
        boolean systemFound = gateway.listProductSystems(
                        connection.baseUrl(),
                        token,
                        productId)
                .stream()
                .anyMatch(item -> systemId.equals(item.id()));
        if (!systemFound) {
            throw new DomainException("所属应用不存在或与产品不匹配");
        }
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

    private List<String> normalizedTestTypes(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .peek(value -> {
                    if (!TEST_TASK_TYPES.contains(value)) {
                        throw new DomainException("不支持测试类型");
                    }
                })
                .distinct()
                .toList();
    }

    private String normalizedMembers(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    if (!value.matches("[a-zA-Z0-9_.@-]{1,128}")) {
                        throw new DomainException("参与人账号无效");
                    }
                    return value;
                })
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String requiredDate(String value, String message) {
        String normalized = required(value, message, 10);
        try {
            LocalDate.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new DomainException("日期格式应为 YYYY-MM-DD");
        }
        return normalized;
    }

    private String requiredId(String value, String message) {
        String normalized = required(value, message, 64);
        if (!normalized.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new DomainException(message);
        }
        return normalized;
    }

    private String optionalId(String value, String message) {
        String normalized = optional(value, 64);
        if (normalized.isEmpty()) return "";
        if (!normalized.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new DomainException(message);
        }
        return normalized;
    }

    private String optional(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainException("输入内容过长");
        }
        return normalized;
    }

    private String required(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new DomainException(message);
        if (normalized.length() > maxLength) {
            throw new DomainException("输入内容过长");
        }
        return normalized;
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private record ProjectBugs(
            ZentaoGateway.Project project,
            List<ZentaoGateway.RemoteBug> bugs
    ) {
    }
}
