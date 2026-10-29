package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.WorkFileMatch;
import com.pydlove.workcove.development.domain.port.WorkFileSearchGateway;
import com.pydlove.workcove.shared.domain.DomainException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class WorkFileSearchService {

    private static final int MAX_ROOTS = 16;
    private static final int MAX_QUERY_LENGTH = 120;
    private static final int MAX_PATH_LENGTH = 4096;

    private final WorkFileSearchGateway gateway;

    public WorkFileSearchService(WorkFileSearchGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "工作目录文件搜索网关");
    }

    public List<WorkFileMatch> search(
            List<String> rawRoots,
            String rawQuery) {
        String query = value(rawQuery);
        if (query.isEmpty()) return List.of();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new DomainException("搜索关键词过长");
        }
        List<String> roots = normalizeRoots(rawRoots);
        return roots.isEmpty()
                ? List.of()
                : gateway.search(roots, query);
    }

    public void openFile(String rawPath, List<String> rawRoots) {
        String path = normalizePath(rawPath, "文件路径无效");
        List<String> roots = normalizeRoots(rawRoots);
        if (roots.isEmpty()) {
            throw new DomainException("缺少工作目录");
        }
        gateway.openFile(path, roots);
    }

    private static List<String> normalizeRoots(List<String> rawRoots) {
        if (rawRoots == null || rawRoots.isEmpty()) return List.of();
        if (rawRoots.size() > MAX_ROOTS) {
            throw new DomainException("工作目录数量过多");
        }
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        for (String rawRoot : rawRoots) {
            String value = value(rawRoot);
            if (value.isEmpty()) continue;
            roots.add(normalizePath(value, "工作目录路径无效"));
        }
        return new ArrayList<>(roots);
    }

    private static String normalizePath(String rawPath, String message) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new DomainException(message);
        }
        String value = rawPath.trim();
        if (value.length() > MAX_PATH_LENGTH
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            throw new DomainException(message);
        }
        try {
            Path path = Path.of(value);
            if (!path.isAbsolute()) throw new DomainException(message);
            return path.normalize().toString();
        } catch (InvalidPathException exception) {
            throw new DomainException(message, exception);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
