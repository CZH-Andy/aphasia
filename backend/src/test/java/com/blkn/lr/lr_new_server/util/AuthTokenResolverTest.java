package com.blkn.lr.lr_new_server.util;

import com.blkn.lr.lr_new_server.exception.BusinessErrorException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTokenResolverTest {

    @Test
    void shouldResolveBearerTokenCaseInsensitively() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "bearer signed.jwt.token");

        var resolved = AuthTokenResolver.resolve(request);

        assertEquals("signed.jwt.token", resolved.token());
        assertFalse(resolved.legacyHeader());
    }

    @Test
    void shouldResolveLegacyTokenHeaderForCompatibility() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthTokenResolver.LEGACY_TOKEN_HEADER, "legacy-token");

        var resolved = AuthTokenResolver.resolve(request);

        assertEquals("legacy-token", resolved.token());
        assertTrue(resolved.legacyHeader());
    }

    @Test
    void shouldReportMissingCredentials() {
        var resolved = AuthTokenResolver.resolve(new MockHttpServletRequest());

        assertTrue(resolved.isMissing());
        assertFalse(resolved.legacyHeader());
    }

    @Test
    void shouldRejectMalformedAuthorizationHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic credentials");

        BusinessErrorException ex = assertThrows(
                BusinessErrorException.class,
                () -> AuthTokenResolver.resolve(request));

        assertEquals("Authorization必须使用Bearer格式", ex.getMessage());
    }

    @Test
    void shouldRejectDuplicateCredentialMethods() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer modern-token");
        request.addHeader(AuthTokenResolver.LEGACY_TOKEN_HEADER, "legacy-token");

        BusinessErrorException ex = assertThrows(
                BusinessErrorException.class,
                () -> AuthTokenResolver.resolve(request));

        assertEquals("认证凭据不能重复提交", ex.getMessage());
    }

    @Test
    void shouldRejectRepeatedAuthorizationHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer first-token");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer second-token");

        BusinessErrorException ex = assertThrows(
                BusinessErrorException.class,
                () -> AuthTokenResolver.resolve(request));

        assertEquals("Authorization不能重复提交", ex.getMessage());
    }
}
