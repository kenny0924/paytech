package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ConsoleResource;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;

/** 三个资源命令共用的安全审计字段投影，不读写数据库或审计链。 */
public final class ConsoleResourceAuditFacts {
    /** 禁止实例化纯字段投影。 */
    private ConsoleResourceAuditFacts() {}

    /** 更新前冻结原值与候选值，避免成功回写原对象后丢失历史。 */
    public static ConfigurationChanged changed(String actor, long tenantId, ConsoleResource before,
        ConsoleResourceDefinition after, GatewayConfigurationStatus status, int version, String reason, Instant at) {
        var fields = List.of(
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, before == null ? null : before.getDisplayName(), after.getDisplayName()),
            new ConfigurationFieldChange(ConfigurationField.PARENT_RESOURCE, before == null ? null : Objects.toString(before.getParentResourceId(), null), Objects.toString(after.getParentResourceId(), null)),
            new ConfigurationFieldChange(ConfigurationField.COMPONENT_KEY, before == null ? null : before.getComponentKey(), after.getComponentKey()),
            new ConfigurationFieldChange(ConfigurationField.PERMISSION, before == null ? null : Long.toString(before.getPermissionId()), Long.toString(after.getPermissionId())),
            new ConfigurationFieldChange(ConfigurationField.SORT_ORDER, before == null ? null : Integer.toString(before.getSortOrder()), Integer.toString(after.getSortOrder())),
            new ConfigurationFieldChange(ConfigurationField.STATUS, before == null ? null : before.getStatus().getCode(), status.getCode()));
        return new ConfigurationChanged(new Identity(actor, tenantId), new Target("CONSOLE_RESOURCE", after.getResourceCode()),
            before == null ? 0 : before.getVersion(), version, fields, reason, at);
    }

}
