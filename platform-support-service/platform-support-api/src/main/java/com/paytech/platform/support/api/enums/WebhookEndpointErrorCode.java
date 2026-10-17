package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * Operations 维护 Webhook 端点时的地址拒绝原因，HTTP 400。
 * <p>
 * 消费者：Operations BFF 登记后原样转换，Operations Web 按 Code 把安全提示显示在“接收地址”字段下。
 * Client Console 路径暂不使用，仍返回公共 {@code INVALID_REQUEST} / {@code BUSINESS_CHECK_FAILED}。
 */
public enum WebhookEndpointErrorCode implements CodeMessageEnum {
    WEBHOOK_URL_NOT_ALLOWED("地址格式不正确或协议不允许"),

    WEBHOOK_URL_UNRESOLVABLE("端点主机名无法解析"),

    WEBHOOK_URL_NOT_PUBLIC("端点地址指向内网或保留地址");

    private final String message;
    /** 绑定安全提示。 */
    WebhookEndpointErrorCode(String message) { this.message = message; }
    /** 返回稳定代码。 */
    @Override public String getCode() { return name(); }
    /** 返回安全文案。 */
    @Override public String getMessage() { return message; }
}
