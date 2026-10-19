package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.CommercialAgreementClient;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementDetail;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementOverviewResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementTermVersion;
import com.paytech.operations.console.bff.service.processor.request.CommercialAgreementOverviewRequest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 合同详情页聚合：先读合同（参与方、文件、订阅、开通项），再按需读取该 Client 的费率卡取当前与已排期条款版本
 * <li>合同读取失败整页失败；费率卡只覆盖 ACTIVE 的 CLIENT 合同，其他合同标为 NOT_APPLICABLE，不发起读取。</li>
 * <li>费率卡失败或超过 3 秒只把版本部分标为 UNAVAILABLE，不伪造空列表；子线程沿用当前请求 ID。</li>
 * <li>只读组合，不编排任何写入。</li>
 *
 * @author Claude
 */
@Slf4j
@Component
@AllArgsConstructor
public class CommercialAgreementOverviewProcessor
    extends AbstractProcessor<CommercialAgreementOverviewRequest, AgreementOverviewResponseBody> {
    /** 费率卡部分的等待上限。 */
    static final long DEADLINE_SECONDS = 3;

    static final String AVAILABLE = "AVAILABLE";
    static final String UNAVAILABLE = "UNAVAILABLE";
    static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    private final CommercialAgreementClient client;

    @Override
    protected AgreementOverviewResponseBody processRequest(CommercialAgreementOverviewRequest request) {
        AgreementDetail agreement = client.detail(request.getAgreementId());
        var overview = new AgreementOverviewResponseBody();
        overview.setAgreement(agreement);
        if (!coveredByRateCard(agreement)) {
            overview.setTermVersionsStatus(NOT_APPLICABLE);
            overview.setTermVersions(List.of());
            return overview;
        }
        List<AgreementTermVersion> versions = termVersions(agreement);
        overview.setTermVersionsStatus(versions == null ? UNAVAILABLE : AVAILABLE);
        overview.setTermVersions(versions == null ? List.of() : versions);
        return overview;
    }

    /** 费率卡只返回 ACTIVE 的 CLIENT 合同。 */
    static boolean coveredByRateCard(AgreementDetail agreement) {
        return "CLIENT".equals(agreement.getAgreementType()) && "ACTIVE".equals(agreement.getStatus())
            && agreement.getClientId() != null;
    }

    /** 带上限读取费率卡；失败或超时返回 null。 */
    private List<AgreementTermVersion> termVersions(AgreementDetail agreement) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(withRequestContext(
                () -> client.termVersions(agreement.getClientId(), agreement.getAgreementId())));
            try {
                return future.get(DEADLINE_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ServiceUnavailableException();
            } catch (TimeoutException | ExecutionException failure) {
                future.cancel(true);
                log.warn("费率卡读取失败，合同详情的条款版本标为不可用, agreementId={}, cause={}", agreement.getAgreementId(),
                    failure.getClass().getSimpleName());
                return null;
            }
        }
    }
}
