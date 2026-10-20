package com.paytech.operations.console.bff.service.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 商务工作台（F8 仪表盘）的页面模型
 * <li>每张卡片一个指标；数据源未接入时 count 为 null、status 为 PENDING_INTEGRATION，页面显示"待接入"，不以 0 冒充。</li>
 *
 * @author Claude
 */
public final class CommercialWorkbenchModels {

    /** 只承载类型，禁止创建实例。 */
    private CommercialWorkbenchModels() {
    }

    /** 工作台指标卡片。 */
    @Getter
    @Setter
    public static class CommercialDashboardResponseBody {
        /** 生成时点（UTC RFC 3339） */
        private String asOf;

        /** 指标，按卡片展示顺序 */
        private List<DashboardMetric> metrics;
    }

    /** 一张指标卡片。 */
    @Getter
    @Setter
    public static class DashboardMetric {
        /** 指标代码（CommercialDashboardMetric） */
        private String metricCode;

        /** 数量；未接入为 null */
        private Long count;

        /** 数据状态（CommercialDashboardMetricStatus） */
        private String status;
    }
}
