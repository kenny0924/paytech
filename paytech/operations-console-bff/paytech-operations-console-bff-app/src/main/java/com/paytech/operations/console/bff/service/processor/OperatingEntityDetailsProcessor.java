package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.result;
import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.domain.enums.ReadAvailability;
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.integration.QualificationAdminClient;
import com.paytech.operations.console.bff.service.model.entityadmin.response.OperatingEntityPageResponseBody;
import com.paytech.operations.console.bff.service.processor.request.OperatingEntityDetailsRequest;

import lombok.AllArgsConstructor;

/**
 * B2 经营主体详情：先读 client-master 主档（主档事实的 Owner），再并发读取 compliance 的 OE 资格与提供方资格
 * <li>主档失败整页失败（含 404）；资格某部分失败或超时只把该部分标为 UNAVAILABLE，不伪造“未登记”。</li>
 * <li>CLIENT 来源（没有提供方）不读取提供方资格，标为 NOT_APPLICABLE。</li>
 * <li>两个资格读取共享 3 秒总等待上限；子线程沿用当前请求 ID；操作者会话或权限失效属于整页失败。</li>
 */
@Component
@AllArgsConstructor
public class OperatingEntityDetailsProcessor
    extends AbstractProcessor<OperatingEntityDetailsRequest, OperatingEntityPageResponseBody> {
    /** 资格各部分的总等待上限。 */
    static final long DEADLINE_SECONDS = 3;

    private final EntityAdminClient entityAdmin;
    private final QualificationAdminClient qualifications;

    @Override
    protected OperatingEntityPageResponseBody processRequest(OperatingEntityDetailsRequest request) {
        var operator = request.getOperator();
        var entity = entityAdmin.operatingEntity(operator, request.getEntityNo());
        String providerId = entity.getProvider() == null ? null : entity.getProvider().getLocalPartnerId();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
            var own = executor.submit(withRequestContext(
                () -> qualifications.subjectQualifications(operator, QualificationRoleType.OPERATING_ENTITY, entity.getEntityNo())));
            var provider = providerId == null ? null : executor.submit(withRequestContext(
                () -> qualifications.subjectQualifications(operator, QualificationRoleType.LOCAL_PARTNER, providerId)));
            var ownValue = result(own, deadline);
            var providerValue = provider == null ? null : result(provider, deadline);
            String providerStatus = providerId == null ? ReadAvailability.NOT_APPLICABLE.getCode() : availability(providerValue);
            return new OperatingEntityPageResponseBody(entity, availability(ownValue), orEmpty(ownValue), providerStatus,
                orEmpty(providerValue));
        }
    }

    /** 读取结果的可用性标记；null 表示失败或超时。 */
    static String availability(List<QualificationResponseBody> value) {
        return (value == null ? ReadAvailability.UNAVAILABLE : ReadAvailability.AVAILABLE).getCode();
    }

    /** 不可用或不适用时返回空列表，由 *Status 区分。 */
    static List<QualificationResponseBody> orEmpty(List<QualificationResponseBody> value) {
        return value == null ? List.of() : value;
    }
}
