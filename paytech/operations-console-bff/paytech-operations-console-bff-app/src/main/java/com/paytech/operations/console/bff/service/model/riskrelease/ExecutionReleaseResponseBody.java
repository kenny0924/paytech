package com.paytech.operations.console.bff.service.model.riskrelease;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 执行发布详情：发布、八项静态检查与最近一次影子运行。
 */
@Getter
@Setter
public class ExecutionReleaseResponseBody {
    /**
     * 发布 ID
     */
    private String releaseId;

    /**
     * 发布编号
     */
    private String releaseNo;

    /**
     * 决策点编码
     */
    private String pointCode;

    /**
     * 冻结的决策流版本 ID
     */
    private String flowVersionId;

    /**
     * 环境
     */
    private String environment;

    /**
     * 状态
     * <li>未知值原样返回，页面容错显示。</li>
     */
    private String status;

    /**
     * 并发版本
     */
    private int version;

    /**
     * 内容摘要
     */
    private String contentHash;

    /**
     * 激活时间
     * <li>未激活为 null。</li>
     */
    private String activatedTime;

    /**
     * 被替代时间
     * <li>未替代为 null。</li>
     */
    private String supersededTime;

    /**
     * 静态检查
     * <li>尚未校验为空列表。</li>
     */
    private List<StaticCheck> checks;

    /**
     * 最近一次影子运行
     * <li>没有为 null。</li>
     */
    private ShadowRun shadowRun;

    /**
     * 一项静态检查结果。
     */
    @Getter
    @Setter
    public static class StaticCheck {
        /**
         * 检查编码
         */
        private String checkCode;

        /**
         * 检查结果
         * <li>PASSED 或 FAILED。</li>
         */
        private String checkStatus;

        /**
         * 失败代码
         * <li>通过为 null。</li>
         */
        private String errorCode;

        /**
         * 失败摘要
         * <li>通过为 null；不含输入值。</li>
         */
        private String errorSummary;
    }

    /**
     * 影子运行批次汇总；计数类字段在批次完成前为 null。
     */
    @Getter
    @Setter
    public static class ShadowRun {
        /**
         * 批次 ID
         */
        private String shadowRunId;

        /**
         * 基线发布 ID
         */
        private String baselineReleaseId;

        /**
         * 批次状态
         * <li>RUNNING、COMPLETED、FAILED；未知值原样返回。</li>
         */
        private String status;

        /**
         * 目标样本数
         */
        private int sampleTarget;

        /**
         * 实际样本数
         */
        private int sampleCount;

        /**
         * 放行变审核的样本数
         */
        private Integer allowToReviewCount;

        /**
         * 放行变拒绝的样本数
         */
        private Integer allowToDenyCount;

        /**
         * 拒绝或审核变放行的样本数
         */
        private Integer denyToAllowCount;

        /**
         * 认证要求变化的样本数
         */
        private Integer actionChangeCount;

        /**
         * 存在无值特征的样本数
         */
        private Integer featureMissingCount;

        /**
         * 候选执行失败数
         */
        private Integer failedCount;

        /**
         * 候选执行超时数
         */
        private Integer timedOutCount;

        /**
         * 候选耗时 P95，毫秒
         */
        private Integer p95Millis;

        /**
         * 候选耗时 P99，毫秒
         */
        private Integer p99Millis;

        /**
         * 技术门槛结论
         * <li>失败与超时均为 0 时为 PASSED；完成前为 null。</li>
         */
        private String checkStatus;
    }
}
