package com.paytech.operations.console.bff.service.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.domain.enums.CommercialAgreementStatus;
import com.paytech.operations.console.bff.domain.enums.CommercialDashboardMetric;
import com.paytech.operations.console.bff.domain.enums.CommercialTermVersionStatus;
import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.service.model.CommercialListModels.AgreementListRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermVersionListItem;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermVersionListRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialWorkbenchModels.CommercialDashboardResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialWorkbenchModels.DashboardMetric;
import com.paytech.operations.console.bff.service.processor.request.CommercialDashboardRequest;

import lombok.AllArgsConstructor;

/**
 * 商务工作台：五张指标卡片与审批队列
 * <li>并发调用 commercial 按状态列表（每次第 1 页、size=10），卡片数量取各自 total；待审批版本的同一次调用同时给出审批队列
 * （commercial 只筛 PENDING_APPROVAL 时按提交时刻升序）。</li>
 * <li>任一调用失败整页失败并保持 commercial 的错误语义，不以 0 冒充"没有待办"。</li>
 * <li>只读组合，不编排任何写入；审批与驳回由页面调用既有版本命令端点。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialDashboardProcessor extends AbstractProcessor<CommercialDashboardRequest, CommercialDashboardResponseBody> {
    /** 审批队列条数，也是每次计数调用的 size（commercial 允许的最小页）。 */
    static final int APPROVAL_QUEUE_SIZE = 10;

    private final CommercialConsoleQueryClient client;

    @Override
    protected CommercialDashboardResponseBody processRequest(CommercialDashboardRequest request) {
        List<Supplier<PagingResponseBody<?>>> calls = List.of(
            () -> versions(CommercialTermVersionStatus.PENDING_APPROVAL),
            () -> versions(CommercialTermVersionStatus.SCHEDULED),
            () -> agreements(CommercialAgreementStatus.DRAFT),
            () -> agreements(CommercialAgreementStatus.PENDING_SIGNATURE),
            () -> agreements(CommercialAgreementStatus.ACTIVE));
        List<PagingResponseBody<?>> pages = CommercialConcurrentReads.all(calls);
        List<CommercialDashboardMetric> order = List.of(CommercialDashboardMetric.PENDING_APPROVAL_VERSIONS,
            CommercialDashboardMetric.SCHEDULED_VERSIONS, CommercialDashboardMetric.DRAFT_AGREEMENTS,
            CommercialDashboardMetric.PENDING_SIGNATURE_AGREEMENTS, CommercialDashboardMetric.ACTIVE_AGREEMENTS);
        var metrics = new ArrayList<DashboardMetric>();
        for (int index = 0; index < order.size(); index++) {
            metrics.add(metric(order.get(index), pages.get(index).total()));
        }
        var dashboard = new CommercialDashboardResponseBody();
        dashboard.setAsOf(request.getAsOf().toString());
        dashboard.setMetrics(metrics);
        dashboard.setApprovalQueue(pages.getFirst().items().stream().map(TermVersionListItem.class::cast).toList());
        return dashboard;
    }

    /** 某状态的版本第一页。 */
    PagingResponseBody<TermVersionListItem> versions(CommercialTermVersionStatus status) {
        return client.listVersions(new TermVersionListRequestBody(null, null, List.of(status.getCode()), null, null, 1,
            APPROVAL_QUEUE_SIZE));
    }

    /** 某状态的合同第一页（只取 total）。 */
    PagingResponseBody<?> agreements(CommercialAgreementStatus status) {
        return client.listAgreements(new AgreementListRequestBody(null, null, null, List.of(status.getCode()), null, null, 1,
            APPROVAL_QUEUE_SIZE));
    }

    /** 一张指标卡片。 */
    static DashboardMetric metric(CommercialDashboardMetric metric, long count) {
        var card = new DashboardMetric();
        card.setMetricCode(metric.getCode());
        card.setCount(count);
        return card;
    }
}
