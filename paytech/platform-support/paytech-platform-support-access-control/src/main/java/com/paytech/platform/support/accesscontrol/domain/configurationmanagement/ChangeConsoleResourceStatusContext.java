package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ConsoleResource;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已验证保护边界和启用资格的状态变更。 */
@Getter
@AllArgsConstructor
public class ChangeConsoleResourceStatusContext {
    /** 已核对版本的本表原对象。 */
    private final ConsoleResource resource;

    /** 目标状态。 */
    private final GatewayConfigurationStatus status;

    /** 本次 UTC 更新时间。 */
    private final Instant updateTime;
}
