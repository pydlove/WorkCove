package com.pydlove.workcove.development.infrastructure.persistence;

import java.time.Instant;

public class ManagedServerData {

    private Long id;
    private Long ownerId;
    private String workspaceId;
    private String name;
    private String host;
    private Integer port;
    private String username;
    private String passwordCiphertext;
    private String hostKeyAlgorithm;
    private String hostKeyFingerprint;
    private String hostKeyPublicKey;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordCiphertext() {
        return passwordCiphertext;
    }

    public void setPasswordCiphertext(String passwordCiphertext) {
        this.passwordCiphertext = passwordCiphertext;
    }

    public String getHostKeyAlgorithm() {
        return hostKeyAlgorithm;
    }

    public void setHostKeyAlgorithm(String hostKeyAlgorithm) {
        this.hostKeyAlgorithm = hostKeyAlgorithm;
    }

    public String getHostKeyFingerprint() {
        return hostKeyFingerprint;
    }

    public void setHostKeyFingerprint(String hostKeyFingerprint) {
        this.hostKeyFingerprint = hostKeyFingerprint;
    }

    public String getHostKeyPublicKey() {
        return hostKeyPublicKey;
    }

    public void setHostKeyPublicKey(String hostKeyPublicKey) {
        this.hostKeyPublicKey = hostKeyPublicKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
