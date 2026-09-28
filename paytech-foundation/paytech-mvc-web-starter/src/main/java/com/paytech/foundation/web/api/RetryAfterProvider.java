package com.paytech.foundation.web.api;

/**
 * 为明确可重试的 MVC 异常提供受控的 {@code Retry-After} 延迟秒数。
 *
 * <p>该契约不接收 Header 名或文本值。共享异常映射器只接受一秒到一天，
 * 并把秒数自行编码为十进制 Header，从而避免业务异常开放任意响应 Header。
 *
 * @author Codex
 */
@FunctionalInterface
public interface RetryAfterProvider {

    /** 最小允许延迟，单位为秒。 */
    long MIN_DELAY_SECONDS = 1L;

    /** 最大允许延迟，单位为秒。临时失败不允许要求调用方等待超过一天。 */
    long MAX_DELAY_SECONDS = 86_400L;

    /**
     * 获取建议调用方再次尝试前等待的秒数。
     *
     * @return 正整数延迟秒数
     */
    long retryAfterSeconds();
}
