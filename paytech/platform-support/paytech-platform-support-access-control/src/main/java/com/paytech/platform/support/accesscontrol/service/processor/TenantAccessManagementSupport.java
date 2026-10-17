package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantAccess;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantAccessReason;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessGrantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessRepository;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 复用目标范围及关联完整性检查，不重复基础字段校验或操作者授权。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TenantAccessManagementSupport {
    private final TenantAccessRepository tenants;
    private final TenantAccessGrantRepository grants;
    private final TenantAccessMemberRepository members;

    /** 不存在与 INTERNAL 对外统一返回 404。 */
    TenantAccess requireTenant(TenantAccess tenant) {
        if (tenant == null) throw new FailException(CommonErrorCode.NOT_FOUND);
        tenant.requireManagedTenant();
        return tenant;
    }

    /** 成员不存在或不属于路径租户返回 404，关联损坏返回系统错误。 */
    TenantMemberView requireMember(TenantMemberView member) {
        if (member == null) throw new FailException(CommonErrorCode.NOT_FOUND);
        member.requireValidMember();
        return member;
    }

    /** 在同一快照中验证全部授予关联，再使用数据库 UTC 时刻解释来源。 */
    MemberScope scope(long tenantId, long memberId, boolean permissions) {
        var tenant = requireTenant(tenants.tenant(tenantId));
        var member = requireMember(members.member(tenantId, memberId));
        Instant asOf = tenants.currentTime();
        if (grants.invalidGrants(tenantId, memberId) != 0
            || (permissions && grants.invalidPermissions(memberId) != 0))
            throw new ErrorException("租户成员授权记录不一致，请联系管理员检查");
        var reasons = new ArrayList<>(tenant.restrictionReasons());
        if (member.getAccountStatus() != OperationsAccountStatus.ACTIVE) reasons.add(TenantAccessReason.ACCOUNT_INACTIVE);
        if (member.getMemberStatus() != OperationsMemberStatus.ACTIVE) reasons.add(TenantAccessReason.MEMBER_INACTIVE);
        return new MemberScope(asOf, List.copyOf(reasons));
    }

    /** 本次查询的统一计算时刻与基础限制，不跨请求缓存。 */
    @Getter
    @AllArgsConstructor
    static class MemberScope {
        /** 本次数据库 UTC 计算时刻。 */
        private final Instant asOf;
        /** 租户、账号及成员的有序限制原因。 */
        private final List<TenantAccessReason> reasons;
    }
}
