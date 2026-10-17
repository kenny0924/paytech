package com.paytech.platform.support.auditsecurity.domain.audit;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.GatewayChange;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;

/** 业务现场可声明的安全不可变审计事实，不携带持久化对象。 */
public sealed interface OperationsAuditFact permits OperationsAuditFact.Outcome,
    OperationsAuditFact.RoleMutation, OperationsAuditFact.AccountEnabled,
    OperationsAuditFact.AccountDeleted, OperationsAuditFact.GatewayStatusChanged,
    OperationsAuditFact.ConfigurationChanged, OperationsAuditFact.CustomerAuthenticationOutcome, OperationsAuditFact.TenantProjected {

    /** 由 CCT 触发的来源状态和授权效果；不携带任意资料或请求快照。 */
    record TenantProjected(Identity identity, Target target, long ownerRefId, long memberId, int sourceVersion,
        String ownerStatus, boolean created, boolean ownerRoleGranted, Instant occurredAt) implements OperationsAuditFact {
        public TenantProjected {
            requirePresent(identity, target, ownerStatus, occurredAt);
            require(ownerRefId > 0 && memberId > 0 && sourceVersion > 0);
            require(java.util.Set.of("PENDING", "ACTIVE", "SUSPENDED", "TERMINATED").contains(ownerStatus));
        }
    }

    /** CUSTOMER 认证的稳定引用与效果，不允许携带邮箱、密码或 Token。 */
    record CustomerAuthenticationOutcome(Identity identity, Target target, Result result, String reason,
        Instant occurredAt, Long challengeId, String purpose, boolean accountCreated,
        boolean passwordChanged, boolean sessionsRevoked) implements OperationsAuditFact {
        public CustomerAuthenticationOutcome {
            requirePresent(identity, target, result, reason, occurredAt);
            require(reason.matches("[A-Z0-9_]{1,64}"));
            require(challengeId == null || challengeId > 0);
            require(purpose == null || java.util.Set.of("SIGN_UP_EMAIL", "PASSWORD_RESET").contains(purpose));
        }
    }

    /** 配置变更的受控字段，字段名不能由客户端提供。 */
    enum ConfigurationField {
        SECURITY_STATUS,

        CODE,

        CHANNEL,

        DISCOVERY_SERVICE_NAME,

        HTTP_METHOD,

        EXTERNAL_PATH,

        DOWNSTREAM_PATH,

        TARGET,

        SECURITY_PROFILE,

        AUTHORIZATION_MODE,

        PERMISSION,

        SCOPE,

        REQUEST_COOKIE_MODE,

        RESPONSE_COOKIE_MODE,

        IDEMPOTENCY_MODE,

        BODY_LIMIT_BYTES,

        BODY_RECEIVE_TIMEOUT_MILLIS,

        RESPONSE_TIMEOUT_MILLIS,

        HEADERS_DIGEST,

        QUERIES_DIGEST,

        GUARDS_DIGEST,

        DISPLAY_NAME,

        DESCRIPTION,

        PARENT_RESOURCE,

        COMPONENT_KEY,

        FRONTEND_ROUTE,

        SORT_ORDER,

        RESOURCE_TYPE,

        ACTION_CODE,

        RISK_LEVEL,

        STATUS,

        /** Webhook 端点地址。 */
        ENDPOINT_URL,

        /** Webhook 订阅事件类型，逗号分隔。 */
        EVENT_TYPES,

        /** Webhook 当前签名密钥序号；不记录密钥或 Token。 */
        SIGNING_ROTATION,

        /** 外部 API 请求字段组的规范化 SHA-256 摘要。 */
        REQUEST_FIELDS_DIGEST,

        /** 外部 API 响应字段组的规范化 SHA-256 摘要。 */
        RESPONSE_FIELDS_DIGEST,

        /** 接入方出站地址列表的规范化 SHA-256 摘要。 */
        ENDPOINTS_DIGEST,

        /** 原子 API 执行定义的规范化 SHA-256 摘要。 */
        DEFINITION_DIGEST,

        /** 公共脚本参数声明的规范化 SHA-256 摘要。 */
        PARAMETERS_DIGEST,

        /** 公共脚本源码的 SHA-256 摘要。 */
        SOURCE_DIGEST,

        /** 公共脚本执行阶段。 */
        EXECUTION_STAGE,

        /** 通知语言内容的语言。 */
        LOCALE,

        /** 通知标题模板的 SHA-256 摘要。 */
        SUBJECT_DIGEST,

        /** 通知正文模板的 SHA-256 摘要。 */
        BODY_DIGEST,

        /** 通知路由主账号编码。 */
        PRIMARY_PROVIDER,

        /** 通知路由备用账号编码。 */
        FALLBACK_PROVIDER,

        /** 通知发送账号的服务商类型。 */
        PROVIDER_TYPE,

        /** 通知发送账号的服务商区域。 */
        REGION,

        /** 通知发送账号的发件地址。 */
        FROM_ADDRESS,

        /** 通知发送账号的 SES Configuration Set。 */
        CONFIGURATION_SET,

        /** 通知发送账号接收投递事件的 SQS 队列 URL。 */
        EVENT_QUEUE_URL,

        /** 通知发送账号密钥：只记 SET / REPLACED，不记录 Token 或密钥。 */
        CREDENTIAL,

        /** 通知发送账号测试发送的有界结果码。 */
        TEST_RESULT,

        /** 通知发送账号测试收件邮箱的 SHA-256 摘要。 */
        TEST_RECIPIENT_DIGEST
    }

    /** 集合使用规范化指纹，防止完整子集合突破既有审计属性容量。 */
    record ConfigurationFieldChange(ConfigurationField field, String before, String after) {}

    /** 配置字段和版本在实际变更时冻结，后续审计不回查可变对象。 */
    record ConfigurationChanged(Identity identity, Target target, int beforeVersion, int afterVersion,
        List<ConfigurationFieldChange> changes, String reason, Instant occurredAt) implements OperationsAuditFact {
        public ConfigurationChanged {
            requirePresent(identity, target, changes, reason, occurredAt);
            changes = List.copyOf(changes);
            require(changes.size() <= ConfigurationField.values().length);
        }
    }

    Identity identity();

    Instant occurredAt();

    /** 只有安全原因的成功或拒绝事实。 */
    record Outcome(Identity identity, Target target, Result result, String reason,
        Instant occurredAt) implements OperationsAuditFact {
        public Outcome {
            requirePresent(identity, target, result, reason, occurredAt);
            require(result != Result.FAILED);
        }

        public static Outcome succeeded(String actorId, Long tenantId, Target target, String reason,
            Instant occurredAt) {
            return new Outcome(new Identity(actorId, tenantId), target, Result.SUCCEEDED, reason, occurredAt);
        }

        public static Outcome denied(String actorId, Long tenantId, Target target, String reason,
            Instant occurredAt) {
            return new Outcome(new Identity(actorId, tenantId), target, Result.DENIED, reason, occurredAt);
        }
    }

    /** 角色创建、修改或删除的完整权限快照。 */
    record RoleMutation(Identity identity, RoleMutationType type, RoleSnapshot before,
        RoleSnapshot after, String reason, Instant occurredAt) implements OperationsAuditFact {
        public RoleMutation {
            requirePresent(identity, type, after, reason, occurredAt);
            if (type == RoleMutationType.DELETED) {
                requirePresent(before);
            }
        }

        public static RoleMutation changed(String actorId, Long tenantId, RoleSnapshot before,
            RoleSnapshot after, String reason, Instant occurredAt) {
            return new RoleMutation(new Identity(actorId, tenantId), RoleMutationType.CHANGED, before, after,
                reason, occurredAt);
        }

        public static RoleMutation deleted(String actorId, Long tenantId, RoleSnapshot before,
            String reason, Instant occurredAt) {
            requirePresent(before);
            var after = new RoleSnapshot(before.roleId(), before.roleCode(), before.roleName(),
                Math.incrementExact(before.version()), List.of());
            return new RoleMutation(new Identity(actorId, tenantId), RoleMutationType.DELETED, before, after,
                reason, occurredAt);
        }
    }

    /** 角色审计只冻结当前消费者使用的安全字段。 */
    record RoleSnapshot(String roleId, String roleCode, String roleName, int version,
        List<String> permissionIds) {
        public RoleSnapshot {
            requirePresent(roleId, roleCode, roleName, permissionIds);
            require(version >= 0);
            require(permissionIds.size() <= 256);
            permissionIds = List.copyOf(permissionIds);
        }
    }

    enum RoleMutationType {
        CHANGED,

        DELETED
    }

    /** 账号启用所需的原状态；启用后的固定状态由投影器生成。 */
    record AccountEnabled(Identity identity, AccountState account, String memberId, String reason,
        Instant occurredAt) implements OperationsAuditFact {
        public AccountEnabled {
            requirePresent(identity, account, memberId, reason, occurredAt);
        }

        public AccountEnabled(String actorId, Long tenantId, AccountState account, String memberId,
            String reason, Instant occurredAt) {
            this(new Identity(actorId, tenantId), account, memberId, reason, occurredAt);
        }
    }

    /** 人员删除所需的账号、成员与撤销角色快照。 */
    record AccountDeleted(Identity identity, AccountState account, MemberState member,
        List<Long> roleIds, String reason, Instant occurredAt) implements OperationsAuditFact {
        public AccountDeleted {
            requirePresent(identity, account, member, roleIds, reason, occurredAt);
            require(roleIds.size() <= 244);
            roleIds = List.copyOf(roleIds);
        }

        public AccountDeleted(String actorId, AccountState account, MemberState member,
            List<Long> roleIds, String reason, Instant occurredAt) {
            this(new Identity(actorId, member.tenantId()), account, member, roleIds, reason, occurredAt);
        }
    }

    record AccountState(String accountId, String status, int version, int authEpoch) {
        public AccountState {
            requirePresent(accountId, status);
            require(version >= 0);
            require(authEpoch >= 0);
        }
    }

    record MemberState(String memberId, Long tenantId, String status, int version) {
        public MemberState {
            requirePresent(memberId, tenantId, status);
            require(version >= 0);
        }
    }

    /** Gateway Root 状态变化使用已有有界值对象，不暴露原始幂等键。 */
    record GatewayStatusChanged(Identity identity, GatewayChange change,
        Instant occurredAt) implements OperationsAuditFact {
        public GatewayStatusChanged {
            requirePresent(identity, change, occurredAt);
        }

        public GatewayStatusChanged(String actorId, Long tenantId, GatewayChange change, Instant occurredAt) {
            this(new Identity(actorId, tenantId), change, occurredAt);
        }
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("Operations 审计事实不完整");
        }
    }

    private static void requirePresent(Object... values) {
        for (Object value : values) {
            require(value != null);
        }
    }
}
