package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 字典更新的内部请求，公共模板在获取锁前校验。 */
@Getter
@AllArgsConstructor
public class UpdateRejectionReasonRequest {

    /**
     * 永久字典编码。
     * <li>格式合法后才构造资源锁。</li>
     */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String reasonCode;

    /** 调用方期望的正整数修订号。 */
    @Positive
    private final long expectedVersion;

    /**
     * 当前名称。
     * <li>1–100 个码点且不含首尾空白。</li>
     */
    @NotNull
    @Pattern(regexp = "(?!\\p{javaWhitespace})[^\\p{Cc}\\p{Cs}]{1,100}(?<!\\p{javaWhitespace})")
    private final String name;

    /**
     * 可空内部说明。
     * <li>最多 500 个码点，保留换行。</li>
     */
    @Pattern(regexp = "(?:[^\\p{Cc}\\p{Cs}]|[\\n\\r\\t]){0,500}")
    private final String description;

    /**
     * 默认内部文案。
     * <li>1–500 个码点且必须含非空白字符。</li>
     */
    @NotBlank
    @Pattern(regexp = "(?:[^\\p{Cc}\\p{Cs}]|[\\n\\r\\t]){1,500}")
    private final String defaultMessage;

    /** 可信操作者 Account ID。 */
    @Positive
    private final long actorAccountId;
}
