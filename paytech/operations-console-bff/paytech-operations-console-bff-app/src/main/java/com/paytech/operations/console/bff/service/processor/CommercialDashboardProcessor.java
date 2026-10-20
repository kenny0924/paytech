package com.paytech.operations.console.bff.service.processor;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.domain.enums.CommercialDashboardMetric;
import com.paytech.operations.console.bff.domain.enums.CommercialDashboardMetricStatus;
import com.paytech.operations.console.bff.service.model.CommercialWorkbenchModels.CommercialDashboardResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialWorkbenchModels.DashboardMetric;
import com.paytech.operations.console.bff.service.processor.request.CommercialDashboardRequest;

/**
 * 商务工作台指标卡片：待审批版本、已排期版本、待签署合同、生效合同
 * <li>commercial 的合同 / 版本按状态列表接口由 H 线新增，接入前每张卡片 count 为 null、status 为 PENDING_INTEGRATION，
 * 不以 0 冒充"没有待办"，也不调用任何上游。</li>
 *
 * @author Claude
 */
@Component
public class CommercialDashboardProcessor extends AbstractProcessor<CommercialDashboardRequest, CommercialDashboardResponseBody> {

    @Override
    protected CommercialDashboardResponseBody processRequest(CommercialDashboardRequest request) {
        var dashboard = new CommercialDashboardResponseBody();
        dashboard.setAsOf(request.getAsOf().toString());
        // TODO by CLAUDE: I2 接入 commercial GET /agreements/v1?status= 与 GET /commercial-terms/v1/versions?status= 后按 total 计数。
        dashboard.setMetrics(Arrays.stream(CommercialDashboardMetric.values()).map(CommercialDashboardProcessor::pending).toList());
        return dashboard;
    }

    private static DashboardMetric pending(CommercialDashboardMetric metric) {
        var card = new DashboardMetric();
        card.setMetricCode(metric.getCode());
        card.setCount(null);
        card.setStatus(CommercialDashboardMetricStatus.PENDING_INTEGRATION.getCode());
        return card;
    }
}
