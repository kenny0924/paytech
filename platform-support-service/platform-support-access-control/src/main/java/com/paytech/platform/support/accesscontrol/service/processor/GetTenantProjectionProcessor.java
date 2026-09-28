package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantProjectionResult;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionTenantRepository;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetTenantProjectionRequest;

import lombok.AllArgsConstructor;

/** 在同一快照中查询来源及唯一 OWNER，不修补历史缺失记录。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "tenant.projection.enabled", havingValue = "true")
public class GetTenantProjectionProcessor
    extends AbstractProcessor<GetTenantProjectionRequest, TenantProjectionResult> {
    private final TenantProjectionSupport support;
    private final TenantProjectionTransactions transactions;
    private final TenantProjectionTenantRepository tenants;

    @Override
    protected TenantProjectionResult processRequest(GetTenantProjectionRequest request) {
        return transactions.query(() -> {
            var tenant = tenants.find(request.getOwnerRefId());
            if (tenant == null)
                throw new FailException(CommonErrorCode.NOT_FOUND);
            tenant.requireSource();
            return support.result(tenant, support.owner(tenant, null));
        });
    }
}
