package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * Owner 余额查询中调用方需要按错误码区分处理的业务拒绝（HTTP 400）
 * <li>Owner 以 {@code new FailException(code)} 抛出，Problem {@code code} 为枚举 Code、{@code detail} 为默认消息。</li>
 * <li>消费者：Operations BFF 在 {@code HttpServiceClientFactory} 显式注册本枚举并原样返回 400；Operations Web 把 LEDGER_BOOK_NOT_FOUND 落到 entityNo 字段。</li>
 * <li>未列出的业务拒绝仍使用公共 BUSINESS_CHECK_FAILED。</li>
 */
public enum BalanceQueryErrorCode implements CodeMessageEnum {
    /** Client 或 Local Partner 余额查询时该经营主体与币种没有账簿 */
    LEDGER_BOOK_NOT_FOUND("LEDGER_BOOK_NOT_FOUND", "未找到该经营主体与币种的账簿");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    BalanceQueryErrorCode(String code, String message) {
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
