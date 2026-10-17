package com.paytech.platform.support.integration.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.verify.VerifyAccountView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.platform.support.integration.service.processor.request.ListIntegrationChildrenRequest;

import lombok.AllArgsConstructor;

/** V01 验证账号列表：按编码排序，不带参数与凭据；每个接入方最多 50 个，不分页。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListVerifyAccountsProcessor extends AbstractProcessor<ListIntegrationChildrenRequest, List<VerifyAccountView>> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final IntegrationVerifyAccountRepository accounts;
    private final OperationsManagementPolicyService policy;

    @Override
    protected List<VerifyAccountView> processRequest(ListIntegrationChildrenRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
        long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
        return accounts.allByIntegration(integrationId).stream().map(row -> verify.accountView(row, List.of(), false))
            .toList();
    }
}
