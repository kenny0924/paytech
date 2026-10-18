package com.paytech.platform.support.service.processor.notification.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建或修改发送账号的区域、发件地址与 Configuration Set。 */
@Getter
@AllArgsConstructor
public class SaveNotificationProviderRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 账号编码。 */
    private final String providerCode;

    /** 服务商类型；仅新建时使用，修改时为空。 */
    private final String providerType;

    /** 服务商区域。 */
    private final String region;

    /** 发件地址。 */
    private final String fromAddress;

    /** Configuration Set，可空。 */
    private final String configurationSet;

    /** 接收投递事件的 SQS 队列 URL，可空。 */
    private final String eventQueueUrl;

    /** 当前版本，0 为新建。 */
    private final int expectedVersion;

    /** 操作原因。 */
    private final String reason;
}
