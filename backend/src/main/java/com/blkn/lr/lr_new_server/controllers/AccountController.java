package com.blkn.lr.lr_new_server.controllers;

import com.blkn.lr.lr_new_server.dto.common.UserDto;
import com.blkn.lr.lr_new_server.dto.request.LoginRequest;
import com.blkn.lr.lr_new_server.exception.AuthException;
import com.blkn.lr.lr_new_server.services.AccountServices;
import com.blkn.lr.lr_new_server.util.AuthTokenResolver;
import com.blkn.lr.lr_new_server.util.AuthTokenResolver.ResolvedToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/")
@RequiredArgsConstructor
public class AccountController {
    private static final String LEGACY_AUTH_DEPRECATION_DATE = "@1784217600";

    private final AccountServices service;

    @PostMapping("/auth/login")
    public UserDto login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        disableCaching(response);
        return service.loginWithPassword(request);
    }

    @PostMapping("/auth/token")
    public UserDto authenticateWithToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        return authenticateWithToken(request, response, false);
    }

    /**
     * 兼容旧客户端的 Token 认证入口。这里不再读取 identity/password 请求头。
     */
    @Deprecated
    @PostMapping("/auth")
    public UserDto authenticateWithTokenLegacy(
            HttpServletRequest request,
            HttpServletResponse response) {
        return authenticateWithToken(request, response, true);
    }

    @PostMapping("/register")
    public UserDto register(@Valid @RequestBody UserDto dto, HttpServletResponse response) {
        disableCaching(response);
        return service.register(dto);
    }

    private UserDto authenticateWithToken(
            HttpServletRequest request,
            HttpServletResponse response,
            boolean legacyEndpoint) {
        disableCaching(response);
        if (legacyEndpoint) {
            response.setHeader("Deprecation", LEGACY_AUTH_DEPRECATION_DATE);
            response.setHeader("Link", "</api/auth/token>; rel=\"successor-version\"");
        }
        ResolvedToken resolvedToken = AuthTokenResolver.resolve(request);
        if (resolvedToken.isMissing()) {
            response.setHeader(
                    HttpHeaders.WWW_AUTHENTICATE,
                    AuthTokenResolver.bearerChallenge(null));
            throw new AuthException("缺少Token");
        }
        if (resolvedToken.legacyHeader()) {
            response.setHeader(AuthTokenResolver.LEGACY_HEADER_DEPRECATION_RESPONSE, "Token");
        }
        try {
            return service.loginWithToken(resolvedToken.token());
        } catch (AuthException ex) {
            response.setHeader(
                    HttpHeaders.WWW_AUTHENTICATE,
                    AuthTokenResolver.bearerChallenge("invalid_token"));
            throw ex;
        }
    }

    private void disableCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}
