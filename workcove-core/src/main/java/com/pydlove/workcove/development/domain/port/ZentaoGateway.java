package com.pydlove.workcove.development.domain.port;

import java.util.List;

public interface ZentaoGateway {

    String authenticate(String baseUrl, String username, String password);

    List<Project> listProjects(String baseUrl, String token);

    List<Product> listProducts(String baseUrl, String token);

    List<RemoteBug> listProjectBugs(
            String baseUrl,
            String token,
            String projectId,
            String assignedTo);

    void resolveBug(
            String baseUrl,
            String token,
            String remoteBugId,
            String assignedTo,
            String resolution,
            String comment);

    MediaFile fetchMedia(
            String baseUrl,
            String token,
            String source);

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
            String steps,
            String openedBy,
            String openedDate,
            String assignedTo
    ) {
    }
}
