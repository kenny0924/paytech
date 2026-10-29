package com.paytech.platform.support.workflow.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 工作流写入口的资源锁与通知回调投递参数（workflow.*）；启动时读取，修改需重启。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("workflow")
public class WorkflowProperties {

    /**
     * TRY_LOCK 等待时间
     * <li>workflow-instance:{id}、workflow-definition:{id}、workflow-action-template:{id} 共用；默认 500ms。</li>
     */
    @NotNull
    private Duration lockWaitTime = Duration.ofMillis(500);

    /**
     * 资源锁租约
     * <li>默认 30s；版本 CAS 与条件更新仍为最终保护。</li>
     */
    @NotNull
    private Duration lockLeaseTime = Duration.ofSeconds(30);

    /**
     * 通知与回调的尝试上限
     * <li>达到上限置 FAILED 并保留，可人工重试；默认 5（技术方案 4.2）。</li>
     */
    @Min(1)
    @Max(20)
    private int callbackMaxAttempts = 5;

    /**
     * 工作流通知邮件模板编码
     * <li>Delivery 普通模板，需在注册 DML 中登记；默认 WORKFLOW_NOTIFICATION。</li>
     */
    @NotNull
    private String notificationTemplateCode = "WORKFLOW_NOTIFICATION";

    /**
     * 回调事件信封的 sourceService
     * <li>必须与 messaging.producer.service 一致；默认 platform-support。</li>
     */
    @NotNull
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}")
    private String eventSourceService = "platform-support";

    /**
     * 回调事件信封的 instanceId
     * <li>实例清单 instance.id，通常配置为 ${paytech.instance-id}；默认 main。</li>
     */
    @NotNull
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
    private String eventInstanceId = "main";

    /** DATA_QUERY 系统检查参数（workflow.data-query.*，REQ-05）。 */
    @Valid
    @NotNull
    private DataQuery dataQuery = new DataQuery();

    /** DATA_QUERY 系统检查参数；重查间隔由 XXL 调度表达式决定，不在此配置。 */
    @Getter
    @Setter
    public static class DataQuery {

        /**
         * 单次查询超时上限
         * <li>1–8 秒整秒，默认 5s；实际取 min(模板 timeoutSeconds, 本值, data-query 系统上限 8s)。</li>
         */
        @NotNull
        private Duration timeout = Duration.ofSeconds(5);

        /**
         * 检查锁 workflow-action-check:{actionRunId} 的租约
         * <li>默认 30s，须不小于 timeout + 10s；查询期间持有，防止同一动作并发检查。</li>
         */
        @NotNull
        private Duration checkLockLease = Duration.ofSeconds(30);

        /**
         * 定时重查每分片每轮最多检查的动作数
         * <li>1–1000，默认 100。</li>
         */
        @Min(1)
        @Max(1000)
        private int recheckBatchSize = 100;

        /**
         * 定时重查的最小间隔
         * <li>最近检查早于“现在减本值”的动作才进入本轮，避免与立即检查、首查重叠；默认 4m，不小于 0。</li>
         */
        @NotNull
        private Duration recheckMinAge = Duration.ofMinutes(4);

        /**
         * 定时重查每轮耗时上限
         * <li>超过即停止本轮（每 5 分钟调度，避免与下一轮重叠）；默认 240s，须大于 0。</li>
         */
        @NotNull
        private Duration recheckRoundBudget = Duration.ofSeconds(240);

        /** 超时为 1–8 秒整秒。 */
        @AssertTrue(message = "workflow.data-query.timeout 必须是 1–8 秒的整秒")
        public boolean isTimeoutValid() {
            return timeout != null && timeout.toNanos() % 1_000_000_000L == 0 && timeout.getSeconds() >= 1
                && timeout.getSeconds() <= 8;
        }

        /** 检查锁租约不小于超时加 10 秒。 */
        @AssertTrue(message = "workflow.data-query.check-lock-lease 必须不小于 timeout + 10s")
        public boolean isCheckLockLeaseValid() {
            return timeout == null || checkLockLease != null
                && checkLockLease.compareTo(timeout.plusSeconds(10)) >= 0;
        }

        /** 最小间隔不为负，每轮耗时上限为正。 */
        @AssertTrue(message = "workflow.data-query.recheck-min-age 不能为负，recheck-round-budget 必须大于 0")
        public boolean isRecheckWindowValid() {
            return recheckMinAge != null && !recheckMinAge.isNegative() && recheckRoundBudget != null
                && recheckRoundBudget.isPositive();
        }

        /**
         * 传给 data-query 的超时上限秒数。
         *
         * @return 秒数
         */
        public int timeoutSeconds() {
            return Math.toIntExact(timeout.getSeconds());
        }
    }
}
