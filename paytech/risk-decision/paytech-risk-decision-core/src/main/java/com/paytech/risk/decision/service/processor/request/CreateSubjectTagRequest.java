package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 字典创建的内部请求，公共模板在获取锁前校验。 */
@Getter
@AllArgsConstructor
public class CreateSubjectTagRequest {

    /**
     * 永久字典编码。
     * <li>格式合法后才构造资源锁。</li>
     */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String tagCode;

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

    /** 可信操作者 Account ID。 */
    @Positive
    private final long actorAccountId;
}
