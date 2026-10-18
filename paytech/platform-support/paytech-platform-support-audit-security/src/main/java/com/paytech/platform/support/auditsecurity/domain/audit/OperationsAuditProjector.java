package com.paytech.platform.support.auditsecurity.domain.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Attribute;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Fact;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.AccountDeleted;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.AccountEnabled;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.GatewayStatusChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.RoleMutation;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.RoleMutationType;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.RoleSnapshot;

/** 将业务事实映射到现有有序审计属性；不查询业务状态或持久化数据。 */
public final class OperationsAuditProjector {
    private OperationsAuditProjector() {}

    public static Fact project(OperationsAuditFact source) {
        if (source == null) {
            throw new IllegalArgumentException("Operations 审计事实不能为空");
        }
        return switch (source) {
            case OperationsAuditFact.ConfigurationChanged changed -> configurationChanged(changed);
            case Outcome outcome -> outcome(outcome);
            case OperationsAuditFact.CustomerAuthenticationOutcome outcome -> customerAuthentication(outcome);
            case OperationsAuditFact.TenantProjected projection -> tenantProjected(projection);
            case RoleMutation mutation -> roleMutation(mutation);
            case AccountEnabled enabled -> accountEnabled(enabled);
            case AccountDeleted deleted -> accountDeleted(deleted);
            case GatewayStatusChanged changed -> gatewayStatusChanged(changed);
        };
    }

    /** 来源引用及成员授权变化只包含稳定代码和标识。 */
    private static Fact tenantProjected(OperationsAuditFact.TenantProjected source) {
        List<Attribute> attributes = new ArrayList<>();
        add(attributes, "source", "CCT");
        add(attributes, "tenantType", "CLIENT");
        add(attributes, "ownerRefId", Long.toString(source.ownerRefId()));
        add(attributes, "memberId", Long.toString(source.memberId()));
        add(attributes, "sourceVersion", Integer.toString(source.sourceVersion()));
        add(attributes, "ownerStatus", source.ownerStatus());
        add(attributes, "tenantCreated", Boolean.toString(source.created()));
        add(attributes, "onboardingRoleRevoked", Boolean.toString(source.ownerRoleGranted()));
        add(attributes, "ownerRoleGranted", Boolean.toString(source.ownerRoleGranted()));
        return new Fact(source.identity(), source.target(), Result.SUCCEEDED, attributes, source.occurredAt());
    }

    /** 受控字段投影保持在既有 256 个审计属性以内。 */
    private static Fact configurationChanged(OperationsAuditFact.ConfigurationChanged source) {
        List<Attribute> attributes = new ArrayList<>();
        add(attributes, "reason", source.reason());
        add(attributes, "before.version", Integer.toString(source.beforeVersion()));
        add(attributes, "after.version", Integer.toString(source.afterVersion()));
        for (var change : source.changes()) {
            add(attributes, "before." + change.field().name(), change.before() == null ? "" : change.before());
            add(attributes, "after." + change.field().name(), change.after() == null ? "" : change.after());
        }
        return new Fact(source.identity(), source.target(), Result.SUCCEEDED, attributes, source.occurredAt());
    }

    /** Realm/Channel 是固定协议事实；安全效果与原始状态同一次业务提交后投影。 */
    private static Fact customerAuthentication(OperationsAuditFact.CustomerAuthenticationOutcome source) {
        List<Attribute> attributes = new ArrayList<>();
        add(attributes, "reason", source.reason());
        add(attributes, "realm", "CUSTOMER");
        add(attributes, "channel", "CLIENT");
        if (source.challengeId() != null) add(attributes, "challengeId", source.challengeId().toString());
        if (source.purpose() != null) add(attributes, "purpose", source.purpose());
        add(attributes, "accountCreated", Boolean.toString(source.accountCreated()));
        add(attributes, "passwordChanged", Boolean.toString(source.passwordChanged()));
        add(attributes, "sessionsRevoked", Boolean.toString(source.sessionsRevoked()));
        return new Fact(source.identity(), source.target(), source.result(), attributes, source.occurredAt());
    }

    private static Fact outcome(Outcome source) {
        return new Fact(source.identity(), source.target(), source.result(),
            List.of(new Attribute("reason", source.reason())), source.occurredAt());
    }

    private static Fact roleMutation(RoleMutation source) {
        int fixedAttributes = source.type() == RoleMutationType.DELETED ? 10 : 8;
        checkRoleBudget(source.before(), source.after(), fixedAttributes);
        List<Attribute> attributes = new ArrayList<>();
        add(attributes, "reason", source.reason());
        add(attributes, "roleCode", source.after().roleCode());
        addRoleSnapshot(attributes, "before", source.before());
        addRoleSnapshot(attributes, "after", source.after());
        if (source.type() == RoleMutationType.DELETED) {
            add(attributes, "before.status", "ACTIVE");
            add(attributes, "after.status", "INACTIVE");
        }
        return new Fact(source.identity(), new Target("ROLE", source.after().roleId()), Result.SUCCEEDED,
            attributes, source.occurredAt());
    }

    private static Fact accountEnabled(AccountEnabled source) {
        List<Attribute> attributes = new ArrayList<>();
        add(attributes, "reason", source.reason());
        add(attributes, "memberId", source.memberId());
        add(attributes, "before.accountStatus", source.account().status());
        add(attributes, "after.accountStatus", "ACTIVE");
        add(attributes, "before.accountVersion", Integer.toString(source.account().version()));
        add(attributes, "after.accountVersion", Integer.toString(Math.incrementExact(source.account().version())));
        add(attributes, "before.authEpoch", Integer.toString(source.account().authEpoch()));
        add(attributes, "after.authEpoch", Integer.toString(Math.incrementExact(source.account().authEpoch())));
        return new Fact(source.identity(), new Target("USER_ACCOUNT", source.account().accountId()),
            Result.SUCCEEDED, attributes, source.occurredAt());
    }

    private static Fact accountDeleted(AccountDeleted source) {
        List<Attribute> attributes = new ArrayList<>();
        add(attributes, "reason", source.reason());
        add(attributes, "memberId", source.member().memberId());
        add(attributes, "before.accountStatus", source.account().status());
        add(attributes, "after.accountStatus", "CLOSED");
        add(attributes, "before.accountVersion", Integer.toString(source.account().version()));
        add(attributes, "after.accountVersion", Integer.toString(Math.incrementExact(source.account().version())));
        add(attributes, "before.memberStatus", source.member().status());
        add(attributes, "after.memberStatus", "REVOKED");
        add(attributes, "before.memberVersion", Integer.toString(source.member().version()));
        add(attributes, "after.memberVersion", Integer.toString(Math.incrementExact(source.member().version())));
        add(attributes, "before.roleCount", Integer.toString(source.roleIds().size()));
        source.roleIds().stream().sorted().forEach(id -> add(attributes, "before.roleId", Long.toString(id)));
        add(attributes, "after.roleCount", "0");
        return new Fact(source.identity(), new Target("USER_ACCOUNT", source.account().accountId()),
            Result.SUCCEEDED, attributes, source.occurredAt());
    }

    private static Fact gatewayStatusChanged(GatewayStatusChanged source) {
        var change = source.change();
        List<Attribute> attributes = new ArrayList<>();
        add(attributes, "reason", change.reason());
        add(attributes, "sessionId", change.sessionId());
        add(attributes, "before.status", change.before().status());
        add(attributes, "before.version", Integer.toString(change.before().version()));
        add(attributes, "after.status", change.after().status());
        add(attributes, "after.version", Integer.toString(change.after().version()));
        return new Fact(source.identity(), change.target(), Result.SUCCEEDED, attributes, source.occurredAt());
    }

    private static void addRoleSnapshot(List<Attribute> attributes, String prefix, RoleSnapshot role) {
        add(attributes, prefix + ".roleName", role == null ? "" : role.roleName());
        add(attributes, prefix + ".version", role == null ? "0" : Integer.toString(role.version()));
        List<String> permissions = role == null ? List.of() : role.permissionIds();
        add(attributes, prefix + ".permissionCount", Integer.toString(permissions.size()));
        permissions.stream().sorted(Comparator.comparingLong(Long::parseLong))
            .forEach(permission -> add(attributes, prefix + ".permissionId", permission));
    }

    private static void add(List<Attribute> attributes, String key, String value) {
        if (attributes.size() >= 256) {
            throw new IllegalArgumentException("审计属性超过预算");
        }
        attributes.add(new Attribute(key, value));
    }

    private static void checkRoleBudget(RoleSnapshot before, RoleSnapshot after, int fixedAttributes) {
        int beforeSize = before == null ? 0 : before.permissionIds().size();
        int afterSize = after == null ? 0 : after.permissionIds().size();
        if (beforeSize > 256 - fixedAttributes || afterSize > 256 - fixedAttributes - beforeSize) {
            throw new IllegalArgumentException("审计权限快照超过预算");
        }
    }
}
