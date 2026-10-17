package com.paytech.consumer.api.customer;

import com.paytech.consumer.api.enums.ConsumerErrorCode;
import com.paytech.foundation.common.error.FailException;

/**
 * consumerReference 的个人原值拦截：入口已限定字符集（排除 {@code @}、{@code +}、空格），这里再拒绝像电话号码的纯数字。
 * <li>只能拦截明显的个人原值；Client 仍须按接口合同只传不透明引用。</li>
 */
public final class ConsumerReferencePolicy {
    private ConsumerReferencePolicy() {}

    /** 7～15 位纯数字视为电话号码等个人原值，拒绝。 */
    public static void requireOpaque(String consumerReference) {
        if (consumerReference != null && consumerReference.matches("[0-9]{7,15}")) {
            throw new FailException(ConsumerErrorCode.INVALID_CONSUMER_REFERENCE);
        }
    }
}
