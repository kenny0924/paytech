package com.paytech.ledger.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** 调用方需要按错误码区分处理的 ledger 业务错误（HTTP 400）；其余业务拒绝使用公共 BUSINESS_CHECK_FAILED。 */
public enum LedgerErrorCode implements CodeMessageEnum {
    /** 人工调整涉及 Client 或 Local Partner 账户：影响 Owner 余额的调整必须经 billing 登记 MANUAL_ADJUSTMENT 经济事实（design/04 §4） */
    OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN("OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN",
        "人工调整不得涉及 Client 或 Local Partner 账户，请经计费登记 MANUAL_ADJUSTMENT 经济事实");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    LedgerErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 稳定错误 Code。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 可外显的默认消息。 */
    @Override
    public String getMessage() {
        return message;
    }
}
