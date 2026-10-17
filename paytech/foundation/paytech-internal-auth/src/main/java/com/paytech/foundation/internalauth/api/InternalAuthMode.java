package com.paytech.foundation.internalauth.api;

/**
 * 入站内部调用身份的校验模式（{@code internal-auth.mode}）。
 *
 * @author Claude
 */
public enum InternalAuthMode {

    /** 不校验，保持现有行为；默认值。 */
    OFF,

    /** 接受合法 Token；无 Token 或 Token 不合法时回退读取 {@code X-Caller-Service}（迁移期）。 */
    DUAL,

    /** 必须携带合法 Token，否则 401。 */
    REQUIRED
}
