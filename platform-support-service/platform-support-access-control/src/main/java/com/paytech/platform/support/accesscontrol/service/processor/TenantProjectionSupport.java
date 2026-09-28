package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjection;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionMember;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantProjectionResult;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionRepository;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.enums.TenantProjectionErrorCode;

import lombok.AllArgsConstructor;

/** 复用真实申请账号和初始 OWNER 身份检查，不处理请求幂等。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "tenant.projection.enabled", havingValue = "true")
public class TenantProjectionSupport {
    private final TenantProjectionRepository accounts;
    private final TenantProjectionMemberRepository members;

    /** 账号行锁始终先于 Tenant；账号停用仍允许已存在来源收敛。 */
    TenantProjectionAccount lockApplicant(long accountId) {
        var account = accounts.lockAccount(accountId);
        if (account == null || account.realm() != AuthenticationRealm.CUSTOMER
            || account.accountType() != OperationsAccountType.HUMAN)
            throw new FailException(TenantProjectionErrorCode.TENANT_APPLICANT_INVALID);
        return account;
    }

    /** 本期唯一 OWNER 的原申请人不可通过重放替换。 */
    TenantProjectionMember owner(TenantProjection tenant, Long accountId) {
        var owners = members.owners(tenant.tenantId());
        if (owners.size() != 1 || (accountId != null && owners.getFirst().accountId() != accountId))
            throw new FailException(TenantProjectionErrorCode.TENANT_PROJECTION_CONFLICT);
        return owners.getFirst();
    }

    /** 只发布实际对象当前事实，不重复查询或向 Web 暴露 DataObject。 */
    TenantProjectionResult result(TenantProjection tenant, TenantProjectionMember member) {
        return new TenantProjectionResult(tenant.ownerRefId(), member.accountId(), tenant.tenantId(), member.memberId(),
            tenant.ownerStatus(), tenant.sourceVersion(), tenant.sourcePayloadHash());
    }
}
