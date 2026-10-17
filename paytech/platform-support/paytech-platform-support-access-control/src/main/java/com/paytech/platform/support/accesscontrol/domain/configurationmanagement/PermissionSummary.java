package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;

import lombok.Getter;

/** 权限查询的模块输出；只发布必要字段，不暴露持久化对象。 */
@Getter
public class PermissionSummary {
    /** 权限主键。 */
    private final long permissionId;

    /** 不可变权限编码。 */
    private final String permissionCode;

    /** 不可变资源类型。 */
    private final String resourceType;

    /** 不可变动作编码。 */
    private final String actionCode;

    /** 风险等级。 */
    private final OperationsPermissionRiskLevel riskLevel;

    /** 当前状态。 */
    private final PermissionStatus status;

    /** 当前版本。 */
    private final int version;

    /** UTC 创建时间。 */
    private final Instant createTime;

    /** UTC 更新时间。 */
    private final Instant updateTime;

    /** 冻结查询时点的权限字段。 */
    public PermissionSummary(Permission permission) {
        this.permissionId = permission.permissionId();
        this.permissionCode = permission.permissionCode();
        this.resourceType = permission.resourceType();
        this.actionCode = permission.actionCode();
        this.riskLevel = permission.riskLevel();
        this.status = permission.status();
        this.version = permission.version();
        this.createTime = permission.createTime();
        this.updateTime = permission.updateTime();
    }
}
