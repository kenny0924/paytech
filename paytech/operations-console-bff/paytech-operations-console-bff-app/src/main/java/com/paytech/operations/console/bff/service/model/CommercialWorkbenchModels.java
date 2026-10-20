package com.paytech.operations.console.bff.service.model;

import java.util.List;

import com.paytech.operations.console.bff.service.model.CommercialListModels.TermVersionListItem;

import lombok.Getter;
import lombok.Setter;

/**
 * 商务工作台（F2 / F8）的页面模型
 * <li>每张卡片一个指标，数量取自 commercial 按状态列表的 total；审批队列为提交时刻最早的待审批版本。</li>
 *
 * @author Claude
 */
public final class CommercialWorkbenchModels {

    /** 只承载类型，禁止创建实例。 */
    private CommercialWorkbenchModels() {
    }

    /** 工作台指标卡片与审批队列。 */
    @Getter
    @Setter
    public static class CommercialDashboardResponseBody {
        /** 生成时点（UTC RFC 3339） */
        private String asOf;

        /** 指标，按卡片展示顺序 */
        private List<DashboardMetric> metrics;

        /**
         * 审批队列
         * <li>PENDING_APPROVAL 版本按提交时刻升序的前 APPROVAL_QUEUE_SIZE 条；总数见 PENDING_APPROVAL_VERSIONS 指标。</li>
         */
        private List<TermVersionListItem> approvalQueue;
    }

    /** 一张指标卡片。 */
    @Getter
    @Setter
    public static class DashboardMetric {
        /** 指标代码（CommercialDashboardMetric） */
        private String metricCode;

        /** 数量 */
        private long count;
    }
}
