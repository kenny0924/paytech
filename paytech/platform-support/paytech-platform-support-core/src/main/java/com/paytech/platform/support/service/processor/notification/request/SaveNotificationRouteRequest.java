package com.paytech.platform.support.service.processor.notification.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建或修改路由。 */
@Getter
@AllArgsConstructor
public class SaveNotificationRouteRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 模板编码。 */
    private final String templateCode;

    /** 主账号编码。 */
    private final String primaryProviderCode;

    /** 备用账号编码，可空。 */
    private final String fallbackProviderCode;

    /** 当前版本，0 为新建。 */
    private final int expectedVersion;

    /** 操作原因。 */
    private final String reason;
}
