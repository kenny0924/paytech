package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建或修改模板路由的主备账号。 */
@Getter
@AllArgsConstructor
public class SaveNotificationRouteCommand {

    /** 模板编码。 */
    private final String templateCode;

    /** 主账号编码。 */
    private final String primaryProviderCode;

    /** 备用账号编码，可空。 */
    private final String fallbackProviderCode;

    /** 当前版本；0 表示路由尚不存在、新建。 */
    private final int expectedVersion;

    /** 本次写入时间。 */
    private final Instant now;
}
