package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** t_permission 当前事实，不包含角色或持有人授权。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class Permission {
    /** 权限主键。 */
    private long permissionId;

    /** 不可变权限编码。 */
    private String permissionCode;

    /** 不可变资源类型编码。 */
    private String resourceType;

    /** 不可变动作编码。 */
    private String actionCode;

    /** 风险等级：LOW、MEDIUM、HIGH 或 CRITICAL。 */
    private OperationsPermissionRiskLevel riskLevel;

    /** 权限状态：ACTIVE 或 INACTIVE。 */
    private PermissionStatus status;

    /** 当前乐观锁版本。 */
    private int version;

    /** UTC 创建时间。 */
    private Instant createTime;

    /** UTC 更新时间。 */
    private Instant updateTime;

    /** 当前版本通过后仍由数据库 CAS 承担最终保护。 */
    public void requireVersion(int expectedVersion) {
        if (expectedVersion <= 0 || version != expectedVersion) {
            throw new ConcurrentRequestException("权限配置已被其他操作修改，请刷新后重试");
        }
    }

    /** 从本次保存的原对象构造业务回执。 */
    public ConfigurationMutation mutation() {
        return new ConfigurationMutation("PERMISSION", permissionId, permissionCode, status.getCode(), version);
    }
}
