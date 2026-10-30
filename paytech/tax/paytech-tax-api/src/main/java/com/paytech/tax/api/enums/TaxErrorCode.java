package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 调用方需要按错误码区分处理的税务拒绝
 * <li>Owner 以 {@code new FailException(code, detail)} 抛出，Problem {@code code} 为枚举 Code，{@code detail} 含请求维度上下文。</li>
 * <li>HTTP 状态：*_NOT_FOUND 为 404；TAX_CALCULATION_CONFLICT 为 409。</li>
 * <li>消费者：acquiring 遇 TAX_CALCULATION_CONFLICT 说明同一准备身份（source + revision + scope）被以不同内容重复请求，应换新 revision
 * 而不是重试；Operations BFF 遇 TAX_ORDER_NOT_FOUND 显示"订单未找到"空状态。</li>
 * <li>其余业务拒绝使用公共 BUSINESS_CHECK_FAILED，参数错误 INVALID_REQUEST。</li>
 */
public enum TaxErrorCode implements CodeMessageEnum {
    /** 同一计算身份已存在且请求内容不同（HTTP 409） */
    TAX_CALCULATION_CONFLICT("TAX_CALCULATION_CONFLICT", "同一计算身份已以不同内容计算过"),

    /** 按 ID 读取的税务计算不存在（HTTP 404） */
    TAX_CALCULATION_NOT_FOUND("TAX_CALCULATION_NOT_FOUND", "税务计算不存在"),

    /** 订单号下没有任何税务计算（HTTP 404） */
    TAX_ORDER_NOT_FOUND("TAX_ORDER_NOT_FOUND", "订单没有税务记录");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    TaxErrorCode(String code, String message) {
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
