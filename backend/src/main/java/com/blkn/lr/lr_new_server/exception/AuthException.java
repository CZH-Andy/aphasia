package com.blkn.lr.lr_new_server.exception;

/**
 * 登录凭据或会话 Token 无效。与请求格式错误区分，统一返回 HTTP 401。
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
