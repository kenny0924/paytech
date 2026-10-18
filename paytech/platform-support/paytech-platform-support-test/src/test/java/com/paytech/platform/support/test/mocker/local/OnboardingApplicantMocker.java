package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantGrantStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleScope;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleStatus;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.OperationsTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 入驻申请人的本地 IAM 前置数据：CUSTOMER 账号、账号上下文会话，以及持有 TENANT_ONBOARDING_OWNER 的 PENDING CLIENT 租户
 * <li>与 CCT 来源投影首次创建的结构一致；租户由 OperationsMocker 回收，账号、成员、角色与会话由 PlatformDataCleaner 整表回收。</li>
 * <li>本组件不持有可变状态，无需单独清理。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class OnboardingApplicantMocker {

    /**
     * 入驻期间租户所有者的系统角色代码
     * <li>生产在来源投影创建时写入同名角色，未发布常量。</li>
     */
    public static final String ONBOARDING_OWNER_ROLE = "TENANT_ONBOARDING_OWNER";

    private final OperationsMocker operations;
    private final OperationsTestMapper mapper;
    private final ClientWorkspaceMocker workspaces;

    /** 新的入驻申请人：账号、会话与一个属于新 Client 的 PENDING 入驻租户。 */
    public Applicant applicant() {
        long accountId = workspaces.customerAccount("applicant-" + OperatorBuilder.nextId() + "@example.test");
        long sessionId = workspaces.accountSession(accountId);
        long clientId = OperatorBuilder.nextId();
        long tenantId = onboardingTenant(accountId, clientId);
        return new Applicant(accountId, sessionId, tenantId, clientId);
    }

    /**
     * 为同一账号再登记一个属于另一 Client 的 PENDING 入驻租户，使唯一入驻租户的前提不再成立
     *
     * @return 新租户 ID
     */
    public long secondOnboardingTenant(Applicant applicant) {
        return onboardingTenant(applicant.accountId(), OperatorBuilder.nextId());
    }

    /** 插入 PENDING CLIENT 租户、OWNER 成员、入驻所有者系统角色及有效授权。 */
    private long onboardingTenant(long accountId, long clientId) {
        var now = operations.now();
        long tenantId = operations.tenant(OperatorBuilder.nextId(), AccessTenantType.CLIENT.getCode(), clientId,
            "Onboarding applicant fixture");
        assertEquals(1, mapper.projectTenant(tenantId, OperationsTenantOwnerStatus.PENDING.getCode(),
            OnboardingFileBuilder.sha256(Long.toString(tenantId).getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        long memberId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertMember(memberId, tenantId, accountId, TenantMemberType.OWNER.getCode(),
            OperationsMemberStatus.ACTIVE.getCode(), now));
        long roleId = OperatorBuilder.nextId();
        String scope = Long.toString(tenantId);
        assertEquals(1, mapper.insertRole(roleId, TenantRoleScope.TENANT.getCode(), tenantId, scope,
            ONBOARDING_OWNER_ROLE, "入驻所有者", TenantRoleStatus.ACTIVE.getCode(), true, now));
        assertEquals(1, mapper.insertMemberRole(OperatorBuilder.nextId(), memberId, tenantId, roleId, scope,
            now.minusSeconds(1), null, memberId, TenantGrantStatus.ACTIVE.getCode(), now));
        return tenantId;
    }

    /**
     * 测试侧持有的入驻申请人坐标。
     *
     * @param accountId CUSTOMER 账号
     * @param sessionId 账号上下文会话
     * @param tenantId PENDING 入驻租户
     * @param clientId 租户所属 Client，也是 CCT 授权投影中的 clientId
     */
    public record Applicant(long accountId, long sessionId, long tenantId, long clientId) {

        /** 可信账号 Header 值。 */
        public String actor() {
            return Long.toString(accountId);
        }

        /** 可信会话 Header 值。 */
        public String session() {
            return Long.toString(sessionId);
        }
    }
}
