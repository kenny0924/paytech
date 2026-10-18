package com.paytech.vault.client.internal.wire;

/** 旧版迁移读取器专用的四字段 Wire 值；不输出远端内容。 */
public record ErrorResponse(String code, String message, String requestId, String timestamp) {
    @Override public String toString() { return "ErrorResponse[redacted]"; }
}
