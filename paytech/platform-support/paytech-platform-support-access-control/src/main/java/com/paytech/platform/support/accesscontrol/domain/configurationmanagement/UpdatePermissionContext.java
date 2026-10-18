package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** UpdatePermission 本地保存上下文，不承载 HTTP 或基础设施依赖。 */
@Getter
@AllArgsConstructor
public class UpdatePermissionContext {
    /** 已锁定并验证版本的权限。 */
    private final Permission permission;

    /** 目标风险等级。 */
    private final OperationsPermissionRiskLevel riskLevel;

    /** 本次 UTC 微秒时点。 */
    private final Instant updateTime;

    /** 没有变化时不写库或递增版本。 */
    public boolean unchanged() { return permission.riskLevel() == riskLevel; }
}
