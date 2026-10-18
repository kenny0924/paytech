package com.paytech.foundation.internalauth.api;

/**
 * 当前请求线程的调用方身份；由 Servlet 入站过滤器在请求开始时设置、结束时清除。
 * <li>WebFlux 不使用线程绑定，改读 {@code ServerWebExchange} 属性。</li>
 *
 * @author Claude
 */
public final class CallerIdentityHolder {

    /** 请求属性名，Servlet 与 WebFlux 共用。 */
    public static final String REQUEST_ATTRIBUTE = CallerIdentity.class.getName();

    private static final ThreadLocal<CallerIdentity> CURRENT = new ThreadLocal<>();

    private CallerIdentityHolder() {
    }

    /**
     * 当前调用方。
     *
     * @return 身份；未识别时为 null
     */
    public static CallerIdentity current() {
        return CURRENT.get();
    }

    /**
     * 绑定当前线程的调用方。
     *
     * @param identity 身份
     */
    public static void set(CallerIdentity identity) {
        CURRENT.set(identity);
    }

    /**
     * 清除当前线程的调用方。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
