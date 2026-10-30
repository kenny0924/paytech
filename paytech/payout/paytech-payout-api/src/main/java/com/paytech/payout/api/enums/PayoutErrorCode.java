package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 调用方需要按错误码区分处理的收款账户业务拒绝（technique 8.1）
 * <li>Owner 以 {@code new FailException(code, detail)} 抛出，Problem {@code code} 为枚举 Code，{@code detail} 含请求维度上下文。</li>
 * <li>HTTP 状态：BENEFICIARY_DUPLICATE、BENEFICIARY_STATUS_CONFLICT 409；BENEFICIARY_UNSUPPORTED_ROUTE 422；FOUR_EYES_REQUIRED、
 * BENEFICIARY_REVEAL_FORBIDDEN 403；BENEFICIARY_ACCOUNT_NOT_FOUND、BENEFICIARY_REQUIREMENT_NOT_FOUND 404。</li>
 * <li>消费者：Client BFF 与 Operations BFF 按 Code 区分"已有同账号账户（引导查看原账户）""该国家 / 币种暂不支持""需另一人复核"
 * 等提示；其余业务拒绝使用公共 BUSINESS_CHECK_FAILED，参数错误 INVALID_REQUEST，版本冲突 CONCURRENT_REQUEST。</li>
 */
public enum PayoutErrorCode implements CodeMessageEnum {
    /** 同 Owner 已有同账号的未终结账户，HTTP 409；detail 为"同一 Owner 已有相同账号的收款账户: {已有账户 ID}, 状态 {状态}" */
    BENEFICIARY_DUPLICATE("BENEFICIARY_DUPLICATE", "已有相同账号的收款账户"),

    /** 收款方式 × 开户国家 × 币种没有字段清单，HTTP 422 */
    BENEFICIARY_UNSUPPORTED_ROUTE("BENEFICIARY_UNSUPPORTED_ROUTE", "该国家 / 币种暂不支持"),

    /** 当前账户状态不允许该操作（例如非 VERIFIED 设默认、已有 Maker 意见），HTTP 409 */
    BENEFICIARY_STATUS_CONFLICT("BENEFICIARY_STATUS_CONFLICT", "收款账户当前状态不允许该操作"),

    /** 复核人与 Maker 或运营代提交人为同一人，HTTP 403 */
    FOUR_EYES_REQUIRED("FOUR_EYES_REQUIRED", "需由另一人复核"),

    /** 调用方无权读取收款账户原值，HTTP 403 */
    BENEFICIARY_REVEAL_FORBIDDEN("BENEFICIARY_REVEAL_FORBIDDEN", "无权查看收款账户原值"),

    /** 收款账户不存在或不属于请求的 Owner，HTTP 404 */
    BENEFICIARY_ACCOUNT_NOT_FOUND("BENEFICIARY_ACCOUNT_NOT_FOUND", "收款账户不存在"),

    /** 查询的字段要求不存在，HTTP 404 */
    BENEFICIARY_REQUIREMENT_NOT_FOUND("BENEFICIARY_REQUIREMENT_NOT_FOUND", "该国家 / 币种没有字段要求");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    PayoutErrorCode(String code, String message) {
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
