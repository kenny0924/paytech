package com.paytech.client.console.bff.service.processor;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.domain.enums.SectionAvailability;
import com.paytech.client.console.bff.integration.CctOnboardingClient;
import com.paytech.client.console.bff.integration.ComplianceOnboardingClient;
import com.paytech.client.console.bff.integration.PlatformAuthenticationClient;
import com.paytech.client.console.bff.service.model.FormOptionsResponseBody;
import com.paytech.client.console.bff.service.processor.request.ClientFormOptionsRequest;
import com.paytech.client.contract.api.onboarding.response.ClientFormOptionsResponseBody;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementBody;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * C03 受控表单目录：并发组合 CCT 通用目录与 Compliance 主主体材料要求
 * <li>一个 Owner 不可用时保留另一方事实，分别返回 catalogStatus 与 requirementsStatus；材料要求不按国家区分。</li>
 * <li>客户类型在第一次会话回读之后校验，会话失效优先于参数错误；两个 Owner 共享 3 秒等待上限，返回前再次回读会话。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientFormOptionsProcessor extends AbstractProcessor<ClientFormOptionsRequest, FormOptionsResponseBody> {
    /** 两个 Owner 的总等待上限。 */
    static final long DEADLINE_SECONDS = 3;

    private final CctOnboardingClient onboarding;
    private final ComplianceOnboardingClient compliance;
    private final PlatformAuthenticationClient authentication;

    @Override
    protected FormOptionsResponseBody processRequest(ClientFormOptionsRequest request) {
        var actor = request.getActor();
        String clientType = request.getClientType();
        authentication.context(actor);
        if (!OnboardingQualifications.CLIENT_TYPES.contains(clientType)) {
            throw new InvalidParameterException("客户类型无效");
        }
        var executor = Executors.newFixedThreadPool(2, Thread.ofVirtual().factory());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
        try {
            var catalogFuture = executor
                .submit(ConcurrentPageReads.withRequestContext(() -> onboarding.formOptions(actor, clientType)));
            var requirementsFuture = executor.submit(ConcurrentPageReads
                .withRequestContext(() -> compliance.requirements(OnboardingQualifications.primary(clientType))));
            var catalog = ConcurrentPageReads.result(catalogFuture, deadline);
            var requirements = ConcurrentPageReads.result(requirementsFuture, deadline);
            authentication.context(actor);
            return response(clientType, catalog, requirements);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 组装页面目录；不可用的一方返回空列表与 UNAVAILABLE。 */
    static FormOptionsResponseBody response(String clientType, ClientFormOptionsResponseBody catalog,
        List<RequirementBody> requirements) {
        return new FormOptionsResponseBody(clientType,
            catalog == null ? SectionAvailability.UNAVAILABLE.getCode() : catalog.getCatalogStatus(),
            catalog == null ? List.of() : catalog.getOrganizationTypes(),
            catalog == null ? List.of() : catalog.getBusinessModels(),
            catalog == null ? List.of() : catalog.getIdentifierTypes(), SectionAvailability.of(requirements),
            requirements == null ? List.of() : requirements);
    }
}
