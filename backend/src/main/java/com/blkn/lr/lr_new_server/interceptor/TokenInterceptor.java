package com.blkn.lr.lr_new_server.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.blkn.lr.lr_new_server.exception.BusinessErrorException;
import com.blkn.lr.lr_new_server.util.AuthTokenResolver;
import com.blkn.lr.lr_new_server.util.AuthTokenResolver.ResolvedToken;
import com.blkn.lr.lr_new_server.util.TokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

@Slf4j
public class TokenInterceptor implements HandlerInterceptor {
	private final TokenUtil tokenUtil;

	public TokenInterceptor(TokenUtil tokenUtil) {
		this.tokenUtil = tokenUtil;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (request.getMethod().equals("OPTIONS")) {
			return true;
		}

		ResolvedToken resolvedToken;
		try {
			resolvedToken = AuthTokenResolver.resolve(request);
		} catch (BusinessErrorException ex) {
			writeBearerError(response, 400, "invalid_request", ex.getMessage());
			return false;
		}

		if (resolvedToken.isMissing()) {
			writeBearerError(response, 401, null, "缺少Token");
			return false;
		}

		if (resolvedToken.legacyHeader()) {
			response.setHeader(AuthTokenResolver.LEGACY_HEADER_DEPRECATION_RESPONSE, "Token");
		}

		DecodedJWT decodedJWT = tokenUtil.verifyToken(resolvedToken.token());
		if (decodedJWT == null) {
			writeBearerError(response, 401, "invalid_token", "Token无效或已过期");
			return false;
		}

		String uid = decodedJWT.getClaim("uid").asString();
		Integer uType = decodedJWT.getClaim("uType").asInt();
		if (uid == null || uid.isBlank() || uType == null) {
			writeBearerError(response, 401, "invalid_token", "Token无效或已过期");
			return false;
		}

		request.setAttribute("uid", uid);
		request.setAttribute("uType", uType);

		if (!hasRequiredRole(handler, uType)) {
			writeBearerError(response, 403, "insufficient_scope", "权限不足");
			return false;
		}

		return true;
	}

	private boolean hasRequiredRole(Object handler, int uType) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}

		RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
		if (requireRole == null) {
			requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
		}

		if (requireRole == null) {
			return true;
		}

		return Arrays.stream(requireRole.value()).anyMatch(role -> role == uType);
	}

	private void writeBearerError(
			HttpServletResponse response,
			int status,
			String bearerError,
			String message) {
		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(status);
		response.setHeader(
				HttpHeaders.WWW_AUTHENTICATE,
				AuthTokenResolver.bearerChallenge(bearerError));
		String body = "{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}";
		try (PrintWriter writer = response.getWriter()) {
			writer.print(body);
		} catch (IOException e) {
			log.error("写入错误响应失败", e);
		}
	}
}
