package com.blkn.lr.lr_new_server.util;

import com.blkn.lr.lr_new_server.exception.BusinessErrorException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一解析 API 认证凭据。正式协议使用 Authorization: Bearer，
 * 旧 Token header 只作为单独的迁移兼容路径。
 */
public final class AuthTokenResolver {
    public static final String LEGACY_TOKEN_HEADER = "Token";
    public static final String LEGACY_HEADER_DEPRECATION_RESPONSE = "X-Auth-Header-Deprecation";
    public static final String BEARER_REALM = "aphasia-api";

    private static final Pattern BEARER_PATTERN =
            Pattern.compile("^Bearer +([A-Za-z0-9\\-._~+/]+=*)$", Pattern.CASE_INSENSITIVE);

    private AuthTokenResolver() {
    }

    public static String bearerChallenge(String error) {
        String challenge = "Bearer realm=\"" + BEARER_REALM + "\"";
        if (error != null) {
            challenge += ", error=\"" + error + "\"";
        }
        return challenge;
    }

    public static ResolvedToken resolve(HttpServletRequest request) {
        String authorization = readSingleHeader(request, HttpHeaders.AUTHORIZATION);
        String legacyToken = readSingleHeader(request, LEGACY_TOKEN_HEADER);

        if (authorization != null && legacyToken != null) {
            throw new BusinessErrorException("认证凭据不能重复提交");
        }

        if (authorization != null) {
            Matcher matcher = BEARER_PATTERN.matcher(authorization);
            if (!matcher.matches()) {
                throw new BusinessErrorException("Authorization必须使用Bearer格式");
            }
            return new ResolvedToken(matcher.group(1), false);
        }

        if (legacyToken != null) {
            return new ResolvedToken(legacyToken, true);
        }

        return new ResolvedToken(null, false);
    }

    private static String readSingleHeader(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        String resolved = null;
        while (values.hasMoreElements()) {
            String value = trimToNull(values.nextElement());
            if (value == null) {
                continue;
            }
            if (resolved != null) {
                throw new BusinessErrorException(headerName + "不能重复提交");
            }
            resolved = value;
        }
        return resolved;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ResolvedToken(String token, boolean legacyHeader) {
        public boolean isMissing() {
            return token == null;
        }
    }
}
