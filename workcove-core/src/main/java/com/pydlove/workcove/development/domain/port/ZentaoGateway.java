package com.pydlove.workcove.development.domain.port;

import java.util.List;

public interface ZentaoGateway {

    String authenticate(String baseUrl, String username, String password);

    List<Project> listProjects(String baseUrl, String token);

    List<Product> listProducts(String baseUrl, String token);

    Product getProduct(
            String baseUrl,
            String token,
            String productId);

    List<RemoteBug> listProjectBugs(
            String baseUrl,
            String token,
            String projectId,
            String assignedTo);

    List<RemoteBuild> listProjectBuilds(
            String baseUrl,
            String token,
            String projectId);

    List<RemoteExecution> listProjectExecutions(
            String baseUrl,
            String token,
            String projectId);

    List<RemoteSystem> listProductSystems(
            String baseUrl,
            String token,
            String productId);

    List<RemoteUser> listUsers(
            String baseUrl,
            String token);

    List<RemoteTestReport> listProductTestReports(
            String baseUrl,
            String token,
            String productId);

    CreatedBuild createBuild(
            String baseUrl,
            String token,
            CreateBuild createBuild);

    CreatedTestTask createTestTask(
            String baseUrl,
            String token,
            CreateTestTask createTestTask);

    void resolveBug(
            String baseUrl,
            String token,
            String remoteBugId,
            String assignedTo,
            String resolution,
            String comment);

    void confirmBug(
            String baseUrl,
            String token,
            String remoteBugId,
            String assignedTo,
            String comment);

    boolean commentBug(
            String baseUrl,
            String token,
            String remoteBugId,
            String comment,
            String marker);

    boolean hasBugComment(
            String baseUrl,
            String token,
            String remoteBugId,
            String marker);

    List<UploadedBugFile> uploadBugFiles(
            String baseUrl,
            String token,
            String remoteBugId,
            List<UploadFile> files);

    MediaFile fetchMedia(
            String baseUrl,
            String token,
            String source);

    record UploadFile(
            String filename,
            String contentType,
            byte[] content
    ) {
    }

    record UploadedBugFile(
            String id,
            String filename,
            String url
    ) {
    }

    record MediaFile(
            byte[] content,
            String contentType
    ) {
    }

    record Product(String id, String name) {
    }

    record Project(String id, String name, String status) {
    }

    record RemoteBug(
            String id,
            String projectId,
            String projectName,
            String productId,
            String title,
            String severity,
            String priority,
            String status,
            boolean confirmed,
            String steps,
            String openedBy,
            String openedDate,
            String assignedTo
    ) {
    }

    record RemoteBuild(
            String id,
            String projectId,
            String productId,
            String executionId,
            String name,
            String status,
            String builder,
            String buildDate,
            String description,
            String scmRevision
    ) {
    }

    record RemoteExecution(
            String id,
            String projectId,
            String productId,
            String name,
            String status
    ) {
    }

    record RemoteSystem(
            String id,
            String productId,
            String name
    ) {
    }

    record RemoteUser(
            String account,
            String name
    ) {
    }

    record RemoteTestReport(
            String id,
            String title
    ) {
    }

    record CreateBuild(
            String projectId,
            String executionId,
            String productId,
            String systemId,
            String name,
            String builder,
            String date,
            String downloadUrl
    ) {
    }

    record CreateTestTask(
            String projectId,
            String executionId,
            String productId,
            String buildId,
            List<String> types,
            String owner,
            String members,
            String status,
            String begin,
            String end,
            String testReport,
            String name
    ) {
    }

    record CreatedBuild(
            String id,
            String name
    ) {
    }

    record CreatedTestTask(
            String id,
            String name
    ) {
    }
}
