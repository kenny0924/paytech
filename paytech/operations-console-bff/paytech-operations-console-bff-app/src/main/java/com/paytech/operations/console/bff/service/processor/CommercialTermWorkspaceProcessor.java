package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.CommercialAgreementClient;
import com.paytech.operations.console.bff.integration.CommercialPricingClient;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementDetail;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionPricesResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermWorkspaceResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.TermAgreementScope;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermWorkspaceRequest;

import lombok.AllArgsConstructor;

/**
 * 条款版本编辑页聚合：版本概要 → 版本冻结的合同（可定价 OE 与开通项）→ 每个 OE 的价格全集
 * <li>commercial 的价格读取要求准确 OE，本聚合按合同覆盖的 OE 并发读取后合并，按 OE、币种、优先级排序。</li>
 * <li>任一部分失败整页失败：价格矩阵缺一个 OE 会让运营误以为该 OE 没有价格，不做部分降级。</li>
 * <li>只读组合，不编排任何写入；子线程沿用当前请求 ID。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialTermWorkspaceProcessor
    extends AbstractProcessor<CommercialTermWorkspaceRequest, CommercialTermWorkspaceResponseBody> {
    private final CommercialPricingClient pricing;
    private final CommercialAgreementClient agreements;

    @Override
    protected CommercialTermWorkspaceResponseBody processRequest(CommercialTermWorkspaceRequest request) {
        CommercialTermVersionResponseBody version = pricing.version(request.getVersionId());
        if (version.getAgreementId() == null || version.getTermType() == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        AgreementDetail agreement = agreements.detail(version.getAgreementId());
        TermAgreementScope scope = scope(agreement);
        var workspace = new CommercialTermWorkspaceResponseBody();
        workspace.setVersion(version);
        workspace.setAgreement(scope);
        workspace.setPrices(prices(version, scope.getEntityNos()));
        return workspace;
    }

    /** 可定价 OE：CLIENT 为合同 OE，PARTNER 为覆盖 OE；去重升序。 */
    static TermAgreementScope scope(AgreementDetail agreement) {
        var entityNos = new TreeSet<String>();
        if (agreement.getEntityNo() != null) {
            entityNos.add(agreement.getEntityNo());
        }
        if (agreement.getOperatingEntityNos() != null) {
            agreement.getOperatingEntityNos().stream().filter(Objects::nonNull).forEach(entityNos::add);
        }
        var scope = new TermAgreementScope();
        scope.setAgreementId(agreement.getAgreementId());
        scope.setAgreementNo(agreement.getAgreementNo());
        scope.setAgreementName(agreement.getAgreementName());
        scope.setAgreementType(agreement.getAgreementType());
        scope.setStatus(agreement.getStatus());
        scope.setClientId(agreement.getClientId());
        scope.setLocalPartnerId(agreement.getLocalPartnerId());
        scope.setEntityNos(List.copyOf(entityNos));
        scope.setCapabilities(agreement.getCapabilities() == null ? List.of() : agreement.getCapabilities());
        return scope;
    }

    /** 每个 OE 并发读取价格全集并合并排序。 */
    private List<CommercialPrice> prices(CommercialTermVersionResponseBody version, List<String> entityNos) {
        var merged = new ArrayList<CommercialPrice>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<CommercialTermVersionPricesResponseBody>>();
            for (String entityNo : entityNos) {
                futures.add(executor.submit(withRequestContext(
                    () -> pricing.prices(version.getVersionId(), entityNo, version.getTermType(), null))));
            }
            for (var future : futures) {
                merged.addAll(await(future).getPrices());
            }
        }
        merged.sort(Comparator.comparing(CommercialPrice::getEntityNo, Comparator.nullsLast(String::compareTo))
            .thenComparing(CommercialPrice::getCurrency, Comparator.nullsLast(String::compareTo))
            .thenComparing(CommercialPrice::getPriorityNo, Comparator.nullsLast(Integer::compareTo)));
        return merged;
    }

    /** 子任务失败按原异常抛出，保持 commercial 的错误语义。 */
    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, failure);
        }
    }
}
