package com.paytech.platform.support.delivery.core.dal.dataobject;

import lombok.Getter;
import lombok.Setter;

/** 按租户分组的端点数量（含已停用）；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class WebhookEndpointTenantCount {

    /** CLIENT 租户。 */
    private Long tenantId;

    /** 端点数量。 */
    private Integer endpointCount;
}
