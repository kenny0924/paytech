package com.paytech.gateway.web;

/** 表示 Owner 返回了不能安全外显的响应契约。 */
public final class InvalidUpstreamResponseException extends RuntimeException {
    public InvalidUpstreamResponseException() {
        super("上游响应契约无效");
    }
}
