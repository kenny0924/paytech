package com.paytech.operations.console.bff.service.model;

/**
 * 入站边界已校验的操作人身份；Route Code 只能来自 Gateway 可信 Header。
 *
 * @author Codex
 */
public record OperatorContext(String accountId, String sessionId, String routeCode) {
}
