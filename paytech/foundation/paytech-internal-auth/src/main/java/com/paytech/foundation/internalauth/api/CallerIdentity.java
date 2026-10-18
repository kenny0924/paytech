package com.paytech.foundation.internalauth.api;

import java.util.Objects;

/**
 * 已识别的内部调用方。
 * <li>Token 校验通过时 {@code purpose}、{@code jti} 均非空，{@link #verified()} 为 true。</li>
 * <li>DUAL 模式回退 {@code X-Caller-Service} 时只有调用方名，{@link #verified()} 为 false，不能用于需要签名证明的 Purpose。</li>
 *
 * @author Claude
 */
public final class CallerIdentity {

    private final String caller;
    private final String purpose;
    private final String jti;

    private CallerIdentity(String caller, String purpose, String jti) {
        this.caller = Objects.requireNonNull(caller, "caller must not be null");
        this.purpose = purpose;
        this.jti = jti;
    }

    /**
     * 由合法 Token 得到的身份。
     *
     * @param caller 调用方服务名（{@code sub}）
     * @param purpose Token 声明的 Purpose
     * @param jti Token ID
     * @return 已验证身份
     */
    public static CallerIdentity verified(String caller, String purpose, String jti) {
        return new CallerIdentity(caller, Objects.requireNonNull(purpose, "purpose must not be null"),
            Objects.requireNonNull(jti, "jti must not be null"));
    }

    /**
     * 迁移期从明文头读取的未验证身份。
     *
     * @param caller {@code X-Caller-Service} 的值
     * @return 未验证身份
     */
    public static CallerIdentity legacy(String caller) {
        return new CallerIdentity(caller, null, null);
    }

    /**
     * 调用方服务名。
     *
     * @return {@code spring.application.name}
     */
    public String caller() {
        return caller;
    }

    /**
     * Token 声明的 Purpose。
     *
     * @return 大写 Code；未验证身份为 null
     */
    public String purpose() {
        return purpose;
    }

    /**
     * Token ID。
     *
     * @return Token ID；未验证身份为 null
     */
    public String jti() {
        return jti;
    }

    /**
     * 是否由签名 Token 证明。
     *
     * @return 已验证返回 true
     */
    public boolean verified() {
        return jti != null;
    }

    @Override
    public String toString() {
        return "CallerIdentity[" + caller + (verified() ? ", purpose=" + purpose + ", jti=" + jti : ", legacy") + "]";
    }
}
