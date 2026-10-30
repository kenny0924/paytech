package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskLimitTierClient;
import com.paytech.operations.console.bff.service.processor.request.LimitTierListRequest;
import com.paytech.risk.decision.api.control.response.ClientTierSummaryBody;

import lombok.AllArgsConstructor;

/**
 * 档位列表（L1）：筛选原样转发 Risk 分页，再为本页待审批申请的申请人补显示名
 * <li>Risk 失败整页失败；显示名查询失败只让 requestedByLabel 为 null。</li>
 */
@Component
@AllArgsConstructor
public class LimitTierListProcessor
    extends AbstractProcessor<LimitTierListRequest, PagingResponseBody<ClientTierSummaryBody>> {
    private final LimitTierLabeler labeler;
    private final RiskLimitTierClient tiers;

    @Override
    protected PagingResponseBody<ClientTierSummaryBody> processRequest(LimitTierListRequest request) {
        var paging = request.getPaging();
        var page = tiers.list(request.getOperator(), request.getTier(), request.getPendingApproval(),
            request.getObservationDue(), request.getKeyword(), paging.page(), paging.size());
        return labeler.labelPage(page);
    }
}
