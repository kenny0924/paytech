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
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.integration.QualificationAdminClient;
import com.paytech.operations.console.bff.service.model.entityadmin.response.LocalPartnerPageResponseBody;
import com.paytech.operations.console.bff.service.processor.request.LocalPartnerDetailsRequest;

import lombok.AllArgsConstructor;

/**
 * B5 Local Partner 详情：先读 client-master 主档，再读 compliance 的 Partner 资格
 * <li>主档失败整页失败（含 404）；资格失败或超过 3 秒只标为 UNAVAILABLE，不伪造“未登记”。</li>
 * <li>子线程沿用当前请求 ID；操作者会话或权限失效属于整页失败。</li>
 */
@Component
@AllArgsConstructor
public class LocalPartnerDetailsProcessor extends AbstractProcessor<LocalPartnerDetailsRequest, LocalPartnerPageResponseBody> {
    private final EntityAdminClient entityAdmin;
    private final QualificationAdminClient qualifications;

    @Override
    protected LocalPartnerPageResponseBody processRequest(LocalPartnerDetailsRequest request) {
        var operator = request.getOperator();
        var partner = entityAdmin.localPartner(operator, request.getLocalPartnerId());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(OperatingEntityDetailsProcessor.DEADLINE_SECONDS);
            var own = executor.submit(withRequestContext(
                () -> qualifications.subjectQualifications(operator, QualificationRoleType.LOCAL_PARTNER, partner.getLocalPartnerId())));
            var value = result(own, deadline);
            return new LocalPartnerPageResponseBody(partner, availability(value), orEmpty(value));
        }
    }
}
