package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.result;
import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;
import static com.paytech.operations.console.bff.service.processor.OperatingEntityDetailsProcessor.availability;
import static com.paytech.operations.console.bff.service.processor.OperatingEntityDetailsProcessor.orEmpty;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.OnboardingAdminClient;
import com.paytech.operations.console.bff.integration.QualificationAdminClient;
import com.paytech.operations.console.bff.service.model.clientadmin.response.ClientPageResponseBody;
import com.paytech.operations.console.bff.service.processor.request.ClientDetailsRequest;

import lombok.AllArgsConstructor;

/**
 * CM-11 Client 详情：先读 client-master 主档（含关系人与申请历史），再读 compliance 的 Client 资格
 * <li>主档失败整页失败（含 404 CLIENT_NOT_FOUND）；资格失败或超过 3 秒只标为 UNAVAILABLE，不伪造“未登记”。</li>
 * <li>子线程沿用当前请求 ID；操作者会话或权限失效属于整页失败。</li>
 */
@Component
@AllArgsConstructor
public class ClientDetailsProcessor extends AbstractProcessor<ClientDetailsRequest, ClientPageResponseBody> {
    private final OnboardingAdminClient onboardingAdmin;
    private final QualificationAdminClient qualifications;

    @Override
    protected ClientPageResponseBody processRequest(ClientDetailsRequest request) {
        var operator = request.getOperator();
        var client = onboardingAdmin.client(operator, request.getClientId());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(OperatingEntityDetailsProcessor.DEADLINE_SECONDS);
            var own = executor.submit(withRequestContext(
                () -> qualifications.subjectQualifications(operator, QualificationRoleType.CLIENT, client.getClient().getClientId())));
            var value = result(own, deadline);
            return new ClientPageResponseBody(client, availability(value), orEmpty(value));
        }
    }
}
