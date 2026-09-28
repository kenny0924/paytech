package com.paytech.gateway.routing.snapshot;

/** 表示整份 Platform Snapshot 未通过 Gateway 静态安全边界。 */
public final class InvalidGatewaySnapshotException extends RuntimeException {
    public InvalidGatewaySnapshotException(String message) {
        super(message);
    }
}
