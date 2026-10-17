package com.paytech.platform.support.integration.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.verify.VerifyAccountView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.platform.support.integration.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/** V02 验证账号详情：账号与引用它的用例在同一只读快照中读取。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetVerifyAccountProcessor extends AbstractProcessor<GetIntegrationChildRequest, VerifyAccountView> {

    /** 读取引用方时的上限，超过即只展示前若干个。 */
    static final int MAX_REFERENCES = 100;

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationVerifyAccountRepository accounts;
    private final IntegrationVerifyCaseRepository cases;
    private final OperationsManagementPolicyService policy;

    @Override
    protected VerifyAccountView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
            long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
            IntegrationVerifyAccount row = support.requireFound(accounts.byCode(integrationId, request.getCode()));
            var referenced = cases.list(integrationId, null, 0, MAX_REFERENCES).stream()
                .filter(item -> item.getVerifyAccountId().equals(row.getVerifyAccountId()))
                .map(IntegrationVerifyCase::getCaseCode).sorted().toList();
            return verify.accountView(row, referenced, true);
        });
    }
}
