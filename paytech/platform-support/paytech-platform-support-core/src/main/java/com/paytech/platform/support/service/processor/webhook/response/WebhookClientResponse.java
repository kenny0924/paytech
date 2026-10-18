package com.paytech.platform.support.service.processor.webhook.response;

import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ClientTenant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 搜索结果项。 */
@Getter
@AllArgsConstructor
public class WebhookClientResponse {

    /** CLIENT 租户概况。 */
    private final ClientTenant client;

    /** 端点数量（含已停用）。 */
    private final int endpointCount;
}
