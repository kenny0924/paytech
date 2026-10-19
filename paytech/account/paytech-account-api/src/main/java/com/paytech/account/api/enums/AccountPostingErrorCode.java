package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 过账批次查询与按流重建（{@code /account-postings/v1}）中调用方需要按错误码区分处理的业务拒绝（HTTP 400）
 * <li>Owner 以 {@code new FailException(code)} 抛出，Problem {@code code} 为枚举 Code、{@code detail} 为默认消息。</li>
 * <li>消费者：Operations BFF 原样返回 400，Operations Web 批次详情抽屉显示"批次不存在"；运营重建按 STREAM_NOT_FOUND 提示主体与币种下没有过账流。</li>
 * <li>未列出的业务拒绝仍使用公共 BUSINESS_CHECK_FAILED。</li>
 */
public enum AccountPostingErrorCode implements CodeMessageEnum {
    /** 主体与币种下没有过账流 */
    STREAM_NOT_FOUND("STREAM_NOT_FOUND", "该主体与币种下没有过账流"),

    /** 过账批次不存在 */
    POSTING_NOT_FOUND("POSTING_NOT_FOUND", "过账批次不存在");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    AccountPostingErrorCode(String code, String message) {
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
