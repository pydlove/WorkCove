package com.pydlove.workcove.development.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZentaoBaseUrl {

    private static final Pattern FILE_PLACEHOLDER =
            Pattern.compile("^\\{(\\d+)(\\.[A-Za-z0-9]+)?}$");
    private static final List<String> PAGE_ROUTE_PREFIXES = List.of(
            "api.php",
            "index.php",
            "project-bug-",
            "project-story-",
            "project-task-",
            "project-build-",
            "product-browse-",
            "execution-",
            "bug-",
            "story-",
            "task-",
            "testcase-",
            "user-login",
            "user-logout");

    private ZentaoBaseUrl() {
    }

    public static String normalize(String rawBaseUrl) {
        String value = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getRawAuthority() == null
                    || (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException();
            }
            String contextPath = contextPath(uri.getRawPath());
            return scheme.toLowerCase(Locale.ROOT)
                    + "://"
                    + uri.getRawAuthority()
                    + contextPath;
        } catch (IllegalArgumentException exception) {
            throw new DomainException("请输入有效的 HTTP 或 HTTPS 禅道地址");
        }
    }

    public static URI resolveAsset(
            String rawBaseUrl,
            String rawSource) {
        String source = normalizeAssetSource(rawSource);
        if (source.isEmpty()
                || source.length() > 2048
                || source.contains("\n")
                || source.contains("\r")
                || source.contains("\\")) {
            throw new DomainException("禅道附件地址无效");
        }
        try {
            URI base = URI.create(normalize(rawBaseUrl) + "/");
            URI resolved = base.resolve(URI.create(source));
            if (resolved.getHost() == null
                    || resolved.getRawUserInfo() != null
                    || !sameOrigin(base, resolved)) {
                throw new DomainException("禅道附件地址不在当前禅道站点内");
            }
            return resolved;
        } catch (IllegalArgumentException exception) {
            throw new DomainException("禅道附件地址无效");
        }
    }

    private static String normalizeAssetSource(String rawSource) {
        String source = rawSource == null ? "" : rawSource.trim();
        Matcher placeholder = FILE_PLACEHOLDER.matcher(source);
        if (!placeholder.matches()) return source;
        String extension = placeholder.group(2) == null
                ? ""
                : placeholder.group(2);
        return "file-read-" + placeholder.group(1) + extension;
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String contextPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || "/".equals(rawPath)) {
            return "";
        }
        List<String> contextSegments = new ArrayList<>();
        for (String segment : rawPath.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            if (isPageRoute(segment)) {
                break;
            }
            contextSegments.add(segment);
        }
        return contextSegments.isEmpty()
                ? ""
                : "/" + String.join("/", contextSegments);
    }

    private static boolean isPageRoute(String segment) {
        String normalized = segment.toLowerCase(Locale.ROOT);
        return PAGE_ROUTE_PREFIXES.stream().anyMatch(normalized::startsWith);
    }
}
