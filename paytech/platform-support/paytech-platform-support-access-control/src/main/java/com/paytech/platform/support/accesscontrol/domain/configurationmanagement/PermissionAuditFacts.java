package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;

/** 在 Repository 修改原对象之前冻结审计值，不保存请求或敏感上下文。 */
public final class PermissionAuditFacts {
    private PermissionAuditFacts() {}

    /** 创建的原值为空，版本从 0 到当前初始版本。 */
    public static ConfigurationChanged created(String actor, long tenantId, Permission permission, String reason) {
        return fact(actor, tenantId, permission, null, null, 0, permission.riskLevel(), permission.status(),
            permission.version(), reason, permission.updateTime());
    }

    /** 变更先冻结旧值；调用方只在保存成功后登记该事实。 */
    public static ConfigurationChanged changed(String actor, long tenantId, Permission permission,
        OperationsPermissionRiskLevel riskLevel, PermissionStatus status, String reason, Instant now) {
        return fact(actor, tenantId, permission, permission.riskLevel(), permission.status(), permission.version(),
            riskLevel, status, permission.version() + 1, reason, now);
    }

    private static ConfigurationChanged fact(String actor, long tenantId, Permission permission,
        OperationsPermissionRiskLevel previousRisk, PermissionStatus previousStatus, int previousVersion,
        OperationsPermissionRiskLevel riskLevel, PermissionStatus status, int version, String reason, Instant now) {
        var changes = List.of(
            new ConfigurationFieldChange(ConfigurationField.RESOURCE_TYPE,
                previousVersion == 0 ? null : permission.resourceType(), permission.resourceType()),
            new ConfigurationFieldChange(ConfigurationField.ACTION_CODE,
                previousVersion == 0 ? null : permission.actionCode(), permission.actionCode()),
            new ConfigurationFieldChange(ConfigurationField.RISK_LEVEL,
                previousRisk == null ? null : previousRisk.getCode(), riskLevel.getCode()),
            new ConfigurationFieldChange(ConfigurationField.STATUS,
                previousStatus == null ? null : previousStatus.getCode(), status.getCode()));
        return new ConfigurationChanged(new Identity(actor, tenantId), new Target("PERMISSION", permission.permissionCode()),
            previousVersion, version, changes, reason.strip(), now);
    }
}
