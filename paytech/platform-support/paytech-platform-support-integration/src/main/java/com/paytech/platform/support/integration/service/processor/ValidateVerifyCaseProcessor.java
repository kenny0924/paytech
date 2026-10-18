package com.paytech.platform.support.integration.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.platform.support.integration.service.processor.request.SaveVerifyCaseRequest;

import lombok.AllArgsConstructor;

/** V11 只校验验证用例，不保存、不加锁；账号不存在与步骤问题都以 issues 返回。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ValidateVerifyCaseProcessor extends AbstractProcessor<SaveVerifyCaseRequest, List<ValidationIssue>> {

    private final VerifyCaseChecks checks;
    private final IntegrationChildSupport children;
    private final IntegrationVerifyAccountRepository accounts;
    private final OperationsManagementPolicyService policy;

    @Override
    protected List<ValidationIssue> processRequest(SaveVerifyCaseRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.MANAGE);
        var integration = children.integration(request.getIntegrationCode());
        IntegrationVerifyAccount account = accounts.byCode(integration.getIntegrationId(), request.getAccountCode());
        if (account == null) {
            return List.of(new ValidationIssue("ACCOUNT_NOT_FOUND", "accountCode", "验证账号不存在：" + request.getAccountCode()));
        }
        return checks.issues(integration.getIntegrationCode(), account, request.getSteps());
    }
}
