package com.paytech.platform.support.delivery.integration.webhook;

import java.net.UnknownHostException;

import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;

import lombok.Getter;

/** 端点主机名无法解析或解析到禁止的地址；继承 UnknownHostException 以便经 HttpClient 的 DNS 解析接口抛出。 */
@Getter
public class WebhookAddressException extends UnknownHostException {

    /** DNS_FAILED 或 BLOCKED_ADDRESS。 */
    private final WebhookFailureCode failureCode;

    /** 只记录失败代码，不携带主机名或地址。 */
    public WebhookAddressException(WebhookFailureCode failureCode) {
        super(failureCode.getCode());
        this.failureCode = failureCode;
    }
}
