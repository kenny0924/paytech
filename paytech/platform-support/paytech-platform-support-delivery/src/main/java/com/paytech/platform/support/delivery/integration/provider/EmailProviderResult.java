package com.paytech.platform.support.delivery.integration.provider;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.paytech.foundation.common.enums.CodeEnum;

/** 已校验的供应商业务证据；错误文字不保存外部原文。 */
@Getter
@RequiredArgsConstructor
public class EmailProviderResult {

    private final Outcome outcome;

    private final String messageId;

    private final String code;

    /** 只有确定原请求已经关闭且未来也不会被受理，才可返回 RETRYABLE_REJECTED。 */
    @Getter
    @RequiredArgsConstructor
    public enum Outcome implements CodeEnum {

        ACCEPTED("ACCEPTED"),

        DELIVERED("DELIVERED"),

        RETRYABLE_REJECTED("RETRYABLE_REJECTED"),

        REJECTED("REJECTED"),

        UNDELIVERABLE("UNDELIVERABLE"),

        UNKNOWN("UNKNOWN");

        private final String code;
    }
}
