package com.paytech.vault.client.internal.wire;

/** 不可变内部 Wire 值；不输出远端内容。 */
public record ProtectResponse(String token) {
    @Override public String toString() { return "ProtectResponse[redacted]"; }
}
