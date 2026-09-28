package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建拒绝原因的内部请求，公共 Processor 在获取锁之前校验字段。 */
@Getter
@AllArgsConstructor
public class CreateRejectionReasonRequest {

    /**
     * 永久原因编码。
     * <li>1–64 位大写字母、数字和下划线，以字母开头。</li>
     */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String reasonCode;

    /**
     * 当前名称。
     * <li>1–100 个 Unicode 码点，不允许首尾空白或控制字符。</li>
     */
    @NotNull
    @Pattern(regexp = "(?!\\p{javaWhitespace})[^\\p{Cc}\\p{Cs}]{1,100}(?<!\\p{javaWhitespace})")
    private final String name;

    /**
     * 内部说明。
     * <li>可空或空串，最多 500 个码点，允许换行、回车及制表符。</li>
     */
    @Pattern(regexp = "(?:[^\\p{Cc}\\p{Cs}]|[\\n\\r\\t]){0,500}")
    private final String description;

    /**
     * 默认内部文案。
     * <li>最多 500 个码点且必须包含非空白字符，保留换行。</li>
     */
    @NotBlank
    @Pattern(regexp = "(?:[^\\p{Cc}\\p{Cs}]|[\\n\\r\\t]){1,500}")
    private final String defaultMessage;

    /** 从可信上下文解析的操作者 Account ID。 */
    @Positive
    private final long actorAccountId;
}

