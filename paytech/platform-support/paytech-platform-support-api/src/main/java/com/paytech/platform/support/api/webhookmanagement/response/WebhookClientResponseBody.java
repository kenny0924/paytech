package com.paytech.platform.support.api.webhookmanagement.response;

import lombok.Getter;
import lombok.Setter;

/** Client 搜索结果项。 */
@Getter
@Setter
public class WebhookClientResponseBody {

    /** CCT Client ID，字符串形式。 */
    private String clientId;

    /** Client 名称（Platform 租户名称投影）。 */
    private String clientName;

    /** 来源业务状态：PENDING、ACTIVE、SUSPENDED、TERMINATED。 */
    private String ownerStatus;

    /** Platform 安全状态：ACTIVE、SUSPENDED。 */
    private String securityStatus;

    /** 端点数量（含已停用）。 */
    private int endpointCount;
}
