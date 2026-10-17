package com.paytech.gateway.guard;

/** Guard 所需的 Redis 控制面不可用或返回不可用结果。 */
public final class GuardDependencyUnavailableException extends RuntimeException {
    public GuardDependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public GuardDependencyUnavailableException(String message) {
        super(message);
    }
}
