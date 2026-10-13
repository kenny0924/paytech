package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.test.builder.OperationsCatalog;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dal.OperationsTestMapper;
import com.paytech.platform.support.test.mocker.external.LoginEmailVaultMocker;
import com.paytech.platform.support.test.util.TestAuthenticationKeys;

import lombok.RequiredArgsConstructor;

/**
 * Operations 认证与 IAM 的本地前置数据：全局目录每例重建，人员夹具由用例按需插入。
 *
 * <p>
 * 账号、会话、角色、权限等表由 {@link PlatformDataCleaner} 整表回收；本组件只回收自己插入的租户。
 */
@TestComponent
@RequiredArgsConstructor
public class OperationsMocker implements Clear {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final java.time.Clock clock;
    private final OperationsTestMapper mapper;
    private final TestAuthenticationKeys keys;
    private final LoginEmailVaultMocker vault;
    private final List<Long> tenantIds = new ArrayList<>();

    /** 应用时钟的当前微秒时间；测试应用装配固定时钟，夹具与被测链路使用同一时点。 */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 全局目录：内部租户、两个 Realm 的签名公钥、Platform 权限及两个内置角色。 */
    public void mockCatalog() {
        Instant now = now();
        tenant(OperationsCatalog.INTERNAL_TENANT_ID, "INTERNAL", 1, "Operations");
        signingKey("OPERATIONS", OperationsCatalog.OPERATIONS_KID, now.minusSeconds(60));
        signingKey("CUSTOMER", OperationsCatalog.CUSTOMER_KID, now.minusSeconds(60));
        for (var permission : OperationsCatalog.PERMISSIONS) {
            assertEquals(1, mapper.insertPermission(permission.id(), permission.code(), permission.resourceType(),
                permission.action(), permission.risk(), "ACTIVE", now));
        }
        role(OperationsCatalog.ADMIN_ROLE_ID, "PLATFORM_ADMIN", "平台管理员", true,
            OperationsCatalog.PERMISSIONS.stream().map(OperationsCatalog.Permission::id).toArray(Long[]::new));
        role(OperationsCatalog.VIEWER_ROLE_ID, "OPERATIONS_VIEWER", "运营只读人员", true,
            OperationsCatalog.PERMISSIONS.stream().filter(OperationsCatalog.Permission::viewer)
                .map(OperationsCatalog.Permission::id).toArray(Long[]::new));
    }

    /** 插入一个访问租户，由本组件回收。 */
    public long tenant(long id, String type, long ownerRefId, String name) {
        assertEquals(1, mapper.insertTenant(id, type, ownerRefId, name, "ACTIVE", "ACTIVE", now()));
        tenantIds.add(id);
        return id;
    }

    /** 插入 ACTIVE 签名公钥，公钥由配置私钥推导。 */
    public void signingKey(String realm, String kid, Instant notBefore) {
        assertEquals(1, mapper.insertSigningKey(OperatorBuilder.nextId(), realm, kid, keys.keyRef(realm, kid), 1,
            keys.publicJwk(realm, kid), "ACTIVE", 1, notBefore, null, null, now()));
    }

    /** 插入平台级角色及其 ACTIVE 授权。 */
    public long role(long id, String code, String name, boolean systemRole, Long... permissionIds) {
        Instant now = now();
        assertEquals(1, mapper.insertRole(id, "PLATFORM", null, "PLATFORM", code, name, "ACTIVE", systemRole, now));
        for (Long permissionId : permissionIds) {
            assertEquals(1, mapper.insertRolePermission(OperatorBuilder.nextId(), id, permissionId, "ACTIVE", now));
        }
        return id;
    }

    /** 插入内部租户范围的自定义角色，只授予指定权限编码；与管理接口创建的角色形态一致。 */
    public long customRole(String code, String... permissionCodes) {
        Instant now = now();
        long id = OperatorBuilder.nextId();
        String scope = Long.toString(OperationsCatalog.INTERNAL_TENANT_ID);
        assertEquals(1, mapper.insertRole(id, "TENANT", OperationsCatalog.INTERNAL_TENANT_ID, scope, code, code,
            "ACTIVE", false, now));
        for (String permission : permissionCodes) {
            assertEquals(1, mapper.insertRolePermission(OperatorBuilder.nextId(), id,
                OperationsCatalog.permission(permission).id(), "ACTIVE", now));
        }
        return id;
    }

    /** 插入人员：账号、密码、内部租户成员、角色授权以及可选的 ACTIVE 会话和刷新代。 */
    public Operator operator(OperatorBuilder builder) {
        Instant now = now();
        OperatorBuilder.Spec spec = builder.build();
        String token = null;
        String masked = null;
        if (spec.email() != null) {
            token = vault.allow(spec.email());
            masked = spec.email().charAt(0) + "***" + spec.email().substring(spec.email().indexOf('@'));
        }
        boolean bootstrap = "BOOTSTRAP_ADMIN".equals(spec.type());
        assertEquals(1, mapper.insertAccount(spec.accountId(), "OPERATIONS", spec.type(), bootstrap ? "admin" : null,
            token, masked, null, spec.accountStatus(), spec.lockedUntil(), now));
        assertEquals(1, mapper.insertPassword(OperatorBuilder.nextId(), spec.accountId(),
            keys.passwordPhc(spec.password()), spec.forceChange(), spec.passwordStatus(), now));
        Long memberId = null;
        if (spec.member()) {
            memberId = OperatorBuilder.nextId();
            assertEquals(1, mapper.insertMember(memberId, OperationsCatalog.INTERNAL_TENANT_ID, spec.accountId(),
                "MEMBER", spec.memberStatus(), now));
            for (Long roleId : spec.roleIds()) {
                grant(memberId, roleId, now.minusSeconds(1), null, "ACTIVE");
            }
        }
        if (!spec.session()) {
            return new Operator(spec.accountId(), spec.email(), memberId, null, null, spec.password());
        }
        long sessionId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertSession(sessionId, spec.accountId(), "OPERATIONS", "OPERATIONS",
            memberId == null ? "BOOTSTRAP" : "TENANT", memberId == null ? null : OperationsCatalog.INTERNAL_TENANT_ID,
            memberId, spec.sessionStatus(), now.plus(Duration.ofMinutes(30)), now.plus(Duration.ofHours(8)), now));
        String refresh = refresh("OPERATIONS", "operations-refresh-v1", sessionId, now.plus(Duration.ofHours(8)));
        return new Operator(spec.accountId(), spec.email(), memberId, sessionId, refresh, spec.password());
    }

    /** 为成员追加一条指定有效期和状态的平台角色授权。 */
    public void grant(long memberId, long roleId, Instant from, Instant to, String status) {
        String scope = String.valueOf(mapper.role(roleId).get("scope_key"));
        assertEquals(1, mapper.insertMemberRole(OperatorBuilder.nextId(), memberId,
            OperationsCatalog.INTERNAL_TENANT_ID, roleId, scope, from, to, 1L, status, now()));
    }

    /** 插入当前刷新代并返回原始令牌；秘密只以 HMAC 入库。 */
    public String refresh(String realm, String purpose, long sessionId, Instant expires) {
        String lookup = random(16);
        String raw = lookup + "." + random(32);
        assertEquals(1, mapper.insertRefresh(OperatorBuilder.nextId(), sessionId, lookup,
            keys.hmac(realm, purpose, raw), expires, now()));
        return raw;
    }

    /** 每例开始时没有遗留租户登记。 */
    public void assertEmpty() {
        assertTrue(tenantIds.isEmpty(), "上一例 Operations 租户未回收");
    }

    /** 回收本组件插入的租户；其余表由 PlatformDataCleaner 整表回收。 */
    @Override
    public void clear() {
        try {
            tenantIds.forEach(mapper::deleteTenant);
        } finally {
            tenantIds.clear();
        }
    }

    private static String random(int length) {
        byte[] value = new byte[length];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
