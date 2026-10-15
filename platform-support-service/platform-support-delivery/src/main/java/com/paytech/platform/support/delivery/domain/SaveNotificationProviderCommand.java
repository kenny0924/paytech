package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建发送账号（expectedVersion=0）或按版本修改账号的区域、发件地址与 Configuration Set。 */
@Getter
@AllArgsConstructor
public class SaveNotificationProviderCommand {

    /** 账号编码。 */
    private final String providerCode;

    /**
     * 服务商类型
     * <li>仅新建时使用；修改时忽略，类型创建后不可改</li>
     */
    private final String providerType;

    /** 服务商区域。 */
    private final String region;

    /** 发件地址，尚未规范化。 */
    private final String fromAddress;

    /** Configuration Set，可空。 */
    private final String configurationSet;

    /** 调用方看到的当前版本；0 表示新建。 */
    private final int expectedVersion;

    /** 本次写入时间。 */
    private final Instant now;
}
