package com.paytech.counterparty.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** counterparty 对外稳定错误码目录：原 Commerce Client 错误码中的 Customer 三项，及已存卡上游协议错误（Code 值均与原合同相同）。 */
public enum CounterpartyErrorCode implements CodeMessageEnum {
    /** Customer is unknown or belongs to another Client. */
    CUSTOMER_NOT_FOUND("CUSTOMER_NOT_FOUND", "Customer 不存在"),

    /** Consumer reference looks like a personal raw value such as a phone number. */
    INVALID_CONSUMER_REFERENCE("INVALID_CONSUMER_REFERENCE", "消费者引用不能是 Email、Phone 等个人原值"),

    /** Stored card is unknown or belongs to another Customer. */
    STORED_CARD_NOT_FOUND("STORED_CARD_NOT_FOUND", "已存卡不存在"),

    /** A required downstream response violated the agreed internal protocol (Payment stored cards). */
    UPSTREAM_PROTOCOL_ERROR("UPSTREAM_PROTOCOL_ERROR", "依赖服务响应不符合协议");

    private final String code;
    private final String message;

    CounterpartyErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
