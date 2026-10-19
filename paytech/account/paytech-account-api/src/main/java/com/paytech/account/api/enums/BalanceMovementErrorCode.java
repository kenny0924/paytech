package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 余额桶移动命令（{@code POST /balance-movements/v1}）中调用方需要按错误码区分处理的业务拒绝（HTTP 400）
 * <li>Owner 以 {@code new FailException(code)} 抛出，Problem {@code code} 为枚举 Code、{@code detail} 为默认消息。</li>
 * <li>消费者：后续出款 / 缴税切片的调用方按 Code 区分余额不足（可等待或改金额）与账户不存在、不可用、方向非法（不可重试）。</li>
 * <li>并发冲突使用公共 409；未列出的业务拒绝仍使用公共 BUSINESS_CHECK_FAILED。</li>
 */
public enum BalanceMovementErrorCode implements CodeMessageEnum {
    /** 转出桶余额不足 */
    BALANCE_INSUFFICIENT("BALANCE_INSUFFICIENT", "转出余额桶余额不足"),

    /** 指定主体、币种、科目、Owner 与桶的 Owner 账户不存在 */
    ACCOUNT_NOT_FOUND("ACCOUNT_NOT_FOUND", "Owner 账户不存在"),

    /** Owner 账户为 FROZEN 或 CLOSED */
    ACCOUNT_NOT_ACTIVE("ACCOUNT_NOT_ACTIVE", "Owner 账户已冻结或关闭"),

    /** 来源类型不允许经接口发起，或桶方向、科目与 Owner 类型组合不符合规则 */
    MOVEMENT_NOT_ALLOWED("MOVEMENT_NOT_ALLOWED", "该来源类型不允许此余额移动");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    BalanceMovementErrorCode(String code, String message) {
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
