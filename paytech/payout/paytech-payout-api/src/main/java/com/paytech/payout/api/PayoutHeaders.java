package com.paytech.payout.api;

/**
 * payout 内部接口使用的请求头
 * <li>X-Caller-Service 为过渡信任（同 Platform 通知）：部署限制私网访问，它不是强身份认证；internal-auth 启用后由 Token 身份取代。</li>
 */
public final class PayoutHeaders {
    /** 调用方服务名 Header；运营专属视图（共用提示、Reveal）只对 {@link #OPERATIONS_BFF} 开放。 */
    public static final String CALLER_SERVICE = "X-Caller-Service";

    /** Operations BFF 的服务名。 */
    public static final String OPERATIONS_BFF = "operations-console-bff";

    private PayoutHeaders() {
    }
}
