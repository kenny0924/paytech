package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** ChangePermissionStatus 本地保存上下文，不承载 HTTP 或基础设施依赖。 */
@Getter
@AllArgsConstructor
public class ChangePermissionStatusContext {
    /** 已锁定并验证版本的权限。 */
    private final Permission permission;

    /** 目标权限状态。 */
    private final PermissionStatus status;

    /** 本次 UTC 微秒时点。 */
    private final Instant updateTime;

}
