package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 调用方需要按错误码区分处理的对账业务拒绝（HTTP 400）
 * <li>Owner 以 {@code new FailException(code, detail)} 抛出，Problem {@code code} 为枚举 Code，{@code detail} 含请求维度上下文。</li>
 * <li>消费者：billing 自动支付事实（billing 需求 B）调用清算账户解析时在 {@code HttpServiceClientFactory} 显式注册本枚举；未解析到不能
 * 当作无清算账户继续登记。</li>
 * <li>其余业务拒绝使用公共 BUSINESS_CHECK_FAILED，参数错误 INVALID_REQUEST，并发冲突 CONCURRENT_REQUEST。</li>
 */
public enum ReconciliationErrorCode implements CodeMessageEnum {
    /** MID + 币种没有归属任何清算账户，或归属的清算账户已停用 */
    CLEARING_ACCOUNT_NOT_RESOLVED("CLEARING_ACCOUNT_NOT_RESOLVED", "未解析到有效的清算账户");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    ReconciliationErrorCode(String code, String message) {
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
