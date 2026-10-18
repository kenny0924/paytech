package com.paytech.platform.support.delivery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次 Webhook 配置写入的结果：端点、前后版本与字段差异。 */
@Getter
@AllArgsConstructor
public class WebhookConfigurationChange {

    /** 端点 ID。 */
    private final long endpointId;

    /** 写入前版本；新建时为 0，轮换时为旧密钥序号。 */
    private final int beforeVersion;

    /** 写入后版本；轮换时为新密钥序号。 */
    private final int afterVersion;

    /** 字段差异。 */
    private final List<WebhookFieldChange> changes;
}
