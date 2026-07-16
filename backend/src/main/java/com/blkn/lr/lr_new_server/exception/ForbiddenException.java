package com.blkn.lr.lr_new_server.exception;

/**
 * 已通过身份认证，但无权操作目标资源。
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
