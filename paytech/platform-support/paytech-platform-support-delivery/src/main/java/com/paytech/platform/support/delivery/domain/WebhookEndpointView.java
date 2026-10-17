package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 端点与其当前签名密钥概况的组合查询结果；不含密钥值或 Vault Token。 */
@Getter
@AllArgsConstructor
public class WebhookEndpointView {

    /** 端点 ID。 */
    private final long endpointId;

    /** 端点地址。 */
    private final String url;

    /** 订阅事件类型。 */
    private final List<String> eventTypes;

    /** 端点状态。 */
    private final WebhookEndpointStatus status;

    /** 端点版本。 */
    private final int version;

    /** 创建时间。 */
    private final Instant createTime;

    /** 更新时间。 */
    private final Instant updateTime;

    /** ACTIVE 密钥序号。 */
    private final int rotationNo;

    /** ACTIVE 密钥创建时间。 */
    private final Instant secretCreateTime;

    /** 宽限中的上一把密钥截止时间；不在宽限期时为空。 */
    private final Instant previousValidUntil;
}
