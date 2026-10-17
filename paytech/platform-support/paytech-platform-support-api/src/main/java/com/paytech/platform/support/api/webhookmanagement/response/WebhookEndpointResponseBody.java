package com.paytech.platform.support.api.webhookmanagement.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Webhook 端点及当前签名密钥概况；不含密钥值。 */
@Getter
@Setter
public class WebhookEndpointResponseBody {

    /** 端点 ID，字符串形式。 */
    private String endpointId;

    /** HTTPS 接收地址。 */
    private String url;

    /** 订阅的事件类型。 */
    private List<String> eventTypes;

    /** 端点状态：ACTIVE、DISABLED。 */
    private String status;

    /** 当前签名密钥概况。 */
    private SigningSecretItem signingSecret;

    /** 当前版本。 */
    private int version;

    /** UTC 创建时间。 */
    private String createTime;

    /** UTC 更新时间。 */
    private String updateTime;

    /** 当前签名密钥概况。 */
    @Getter
    @Setter
    public static class SigningSecretItem {

        /** 当前 ACTIVE 密钥是第几把。 */
        private int rotationNo;

        /** 当前 ACTIVE 密钥的 UTC 创建时间。 */
        private String createTime;

        /**
         * 上一把密钥宽限截止时间
         * <li>UTC；不在宽限期内时为空。</li>
         */
        private String previousValidUntil;
    }
}
