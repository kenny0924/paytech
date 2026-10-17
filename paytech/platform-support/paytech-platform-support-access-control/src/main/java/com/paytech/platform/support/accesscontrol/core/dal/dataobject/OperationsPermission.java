package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel;

/** Operations 可授权权限目录的持久化投影。 */
public record OperationsPermission(
    /** 权限主键。 */
    long id,
    /** 稳定权限代码。 */
    String code,
    /** 受控资源类型目录代码。 */
    String resourceType,
    /** 受控操作目录代码。 */
    String actionCode,
    /** 已有权限目录的闭合风险等级。 */
    OperationsPermissionRiskLevel riskLevel) {
}
