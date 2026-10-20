package com.paytech.operations.console.bff.service.processor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.CommercialAgreementClient;
import com.paytech.operations.console.bff.integration.CommercialPricingClient;
import com.paytech.operations.console.bff.service.model.CommercialChangeModels.CommercialTermVersionDiffResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermVersionDiffRequest;

import lombok.AllArgsConstructor;

/**
 * 条款版本对比（F3）：两个版本 → 各自冻结合同的可定价 OE → 每个 OE 的价格全集 → CommercialPriceDiffer 配对比较
 * <li>两个版本必须属于同一方案，否则 400 BUSINESS_CHECK_FAILED；版本、合同、价格读取的 commercial 错误原样透传。</li>
 * <li>价格读取与版本编辑页相同（CommercialConcurrentReads），任一 OE 读取失败整页失败，避免把缺失误报为删除。</li>
 * <li>只读组合，不编排任何写入。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialTermVersionDiffProcessor
    extends AbstractProcessor<CommercialTermVersionDiffRequest, CommercialTermVersionDiffResponseBody> {
    private final CommercialPricingClient pricing;
    private final CommercialAgreementClient agreements;

    @Override
    protected CommercialTermVersionDiffResponseBody processRequest(CommercialTermVersionDiffRequest request) {
        List<CommercialTermVersionResponseBody> versions = CommercialConcurrentReads.all(List.of(
            () -> pricing.version(request.getBaseVersionId()), () -> pricing.version(request.getTargetVersionId())));
        CommercialTermVersionResponseBody base = versions.get(0);
        CommercialTermVersionResponseBody target = versions.get(1);
        requireSamePlan(base, target);
        Map<String, List<String>> entityNos = pricedEntities(base, target);
        List<Supplier<List<CommercialPrice>>> loads = List.of(
            () -> CommercialConcurrentReads.termPrices(pricing, base, entityNos.get(base.getAgreementId())),
            () -> CommercialConcurrentReads.termPrices(pricing, target, entityNos.get(target.getAgreementId())));
        List<List<CommercialPrice>> prices = CommercialConcurrentReads.all(loads);
        return CommercialPriceDiffer.diff(base, target, prices.get(0), prices.get(1));
    }

    /** 两个版本必须带合同与条款类型，并属于同一方案。 */
    static void requireSamePlan(CommercialTermVersionResponseBody base, CommercialTermVersionResponseBody target) {
        for (var version : List.of(base, target)) {
            if (version.getAgreementId() == null || version.getTermType() == null || version.getPlanId() == null) {
                throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
            }
        }
        if (!base.getPlanId().equals(target.getPlanId())) {
            throw new BizCheckFailException("对比的两个版本不属于同一方案: 版本 " + base.getVersionId() + " 属于方案 "
                + base.getPlanId() + ", 版本 " + target.getVersionId() + " 属于方案 " + target.getPlanId());
        }
    }

    /** 每个不同合同读取一次详情，得到可定价 OE（CLIENT 为合同 OE，PARTNER 为覆盖 OE）。 */
    Map<String, List<String>> pricedEntities(CommercialTermVersionResponseBody base, CommercialTermVersionResponseBody target) {
        List<String> agreementIds = List.of(base.getAgreementId(), target.getAgreementId()).stream().distinct().toList();
        List<Supplier<List<String>>> reads = agreementIds.stream().<Supplier<List<String>>>map(agreementId ->
            () -> CommercialTermWorkspaceProcessor.scope(agreements.detail(agreementId)).getEntityNos()).toList();
        List<List<String>> scopes = CommercialConcurrentReads.all(reads);
        var result = new LinkedHashMap<String, List<String>>();
        for (int index = 0; index < agreementIds.size(); index++) {
            result.put(agreementIds.get(index), scopes.get(index));
        }
        return result;
    }
}
