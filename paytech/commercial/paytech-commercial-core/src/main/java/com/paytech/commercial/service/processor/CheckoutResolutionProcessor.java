package com.paytech.commercial.service.processor;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.NO_ELIGIBLE_ENTITY;

import com.paytech.commercial.domain.CheckoutCandidateContext;
import com.paytech.commercial.domain.CheckoutContextView;
import com.paytech.commercial.domain.CheckoutQuery;
import com.paytech.commercial.domain.CheckoutResolutionContext;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.commercial.integration.CheckoutQualificationClient;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 只读解析编排；公共入口拒绝调用方事务，外部资格检查位于两个本地读事务之间。 */
@AllArgsConstructor
public class CheckoutResolutionProcessor extends AbstractProcessor<CheckoutQuery, CheckoutContextView> {
    private final CheckoutQueryService queryService;
    private final CheckoutQualificationClient qualificationClient;

    /** 按优先级检查资格并复核原选择；技术失败直接传播，不降级为候选不支持。 */
    @Override
    protected CheckoutContextView processRequest(CheckoutQuery query) {
        // 同一快照读取本地事实，事务内不调用外部 Owner。
        CheckoutResolutionContext context = queryService.load(query);
        var candidates = context.candidates().iterator();
        if (!candidates.hasNext()) {
            throw new FailException(NO_ELIGIBLE_ENTITY);
        }

        // Client 级拒绝结束整次查询，明确候选不合格才继续。
        qualificationClient.requireClientEligible(context.client().clientId());
        while (candidates.hasNext()) {
            CheckoutCandidateContext candidate = candidates.next();
            if (!qualificationClient.isCandidateEligible(candidate.entityNo(), candidate.localPartnerId())) {
                continue;
            }

            // 新读快照核对相关 Root 和原选择，返回后由 Commerce 冻结。
            queryService.verify(context, candidate);
            return candidate.toView();
        }
        throw new FailException(NO_ELIGIBLE_ENTITY);
    }
}
