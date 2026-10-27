package com.paytech.client.master.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.onboarding.ClientOnboardingApplicationApi;
import com.paytech.client.master.api.onboarding.request.DeclarationRequestBody;
import com.paytech.client.master.api.onboarding.response.DeclarationResponseBody;
import com.paytech.client.master.api.onboarding.response.RequirementsResponseBody;
import com.paytech.client.master.api.onboarding.response.WithdrawalResponseBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.service.processor.GetApplicationRequirementsProcessor;
import com.paytech.client.master.service.processor.RecordApplicationDeclarationProcessor;
import com.paytech.client.master.service.processor.WithdrawClientApplicationProcessor;
import com.paytech.client.master.service.processor.request.ApplicationQueryRequest;
import com.paytech.client.master.service.processor.request.RecordDeclarationRequest;
import com.paytech.client.master.service.processor.request.WithdrawClientApplicationRequest;

import lombok.AllArgsConstructor;

/** CM-6 / CM-7 / CM-8 私网协议适配；只允许 client-console-bff。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(prefix = "onboarding", name = "draft-enabled", havingValue = "true")
public class ClientOnboardingApplicationController implements ClientOnboardingApplicationApi {
    private final RecordApplicationDeclarationProcessor declarations;
    private final GetApplicationRequirementsProcessor requirements;
    private final WithdrawClientApplicationProcessor withdrawals;

    /** 记录声明；IP 与 User-Agent 来自可信入口 Header。 */
    @Override
    public DeclarationResponseBody declare(String caller, String accountId, String sessionId, String clientIp, String userAgent,
        String applicationId, DeclarationRequestBody body) {
        ClientOnboardingCatalogController.caller(caller);
        return declarations.process(new RecordDeclarationRequest(ClientOnboardingCatalogController.session(accountId),
            ClientOnboardingCatalogController.session(sessionId), ClientDraftCommand.id(applicationId), body.getDeclarationType(),
            body.getDeclaredValue(), ClientOnboardingCatalogController.blank(body.getTermsVersion()),
            ClientOnboardingCatalogController.blank(clientIp), ClientOnboardingCatalogController.blank(userAgent)));
    }

    /** 需求清单。 */
    @Override
    public RequirementsResponseBody requirements(String caller, String accountId, String sessionId, String applicationId) {
        ClientOnboardingCatalogController.caller(caller);
        return requirements.process(new ApplicationQueryRequest(ClientOnboardingCatalogController.session(accountId),
            ClientOnboardingCatalogController.session(sessionId), ClientDraftCommand.id(applicationId)));
    }

    /** 撤回。 */
    @Override
    public WithdrawalResponseBody withdraw(String caller, String accountId, String sessionId, String applicationId) {
        ClientOnboardingCatalogController.caller(caller);
        return withdrawals.process(new WithdrawClientApplicationRequest(ClientOnboardingCatalogController.session(accountId),
            ClientOnboardingCatalogController.session(sessionId), ClientDraftCommand.id(applicationId)));
    }
}
