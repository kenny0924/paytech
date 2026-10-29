package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskUnderwritingClient;
import com.paytech.operations.console.bff.service.model.riskunderwriting.LabeledUnderwritingSummaryBody;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingListRequest;

import lombok.AllArgsConstructor;

/**
 * 承保列表（U1）：转发 Risk 分页，"待我复核"时以当前操作人账号作为 pendingReviewFor，再为本页提交人、决定人补显示名
 * <li>Risk 失败整页失败；显示名查询失败只让 *Label 为 null。</li>
 */
@Component
@AllArgsConstructor
public class UnderwritingListProcessor
    extends AbstractProcessor<UnderwritingListRequest, PagingResponseBody<LabeledUnderwritingSummaryBody>> {
    private final UnderwritingLabeler labeler;
    private final RiskUnderwritingClient underwritings;

    @Override
    protected PagingResponseBody<LabeledUnderwritingSummaryBody> processRequest(UnderwritingListRequest request) {
        var operator = request.getOperator();
        var paging = request.getPaging();
        var page = underwritings.list(operator, request.getStatus(), request.getCrr(), request.getDecisionSource(),
            request.getKeyword(), request.getClientId(), request.isPendingReviewForMe() ? operator.accountId() : null,
            paging.page(), paging.size());
        return labeler.labelPage(page);
    }
}
