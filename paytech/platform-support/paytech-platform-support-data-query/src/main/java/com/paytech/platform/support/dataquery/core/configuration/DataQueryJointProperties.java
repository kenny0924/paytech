package com.paytech.platform.support.dataquery.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

/**
 * 联合查询护栏与执行器（前缀 data-query.joint）；修改需重启。
 * <li>单步上限由服务端执行；maxDepth、maxTemplateExecutions、maxTotalSeconds 经设置接口下发，由页面累计执行，
 * 服务端只校验请求层号不超过 maxDepth - 1。</li>
 * <li>stepBudget 不得大于 data-query.limits.max-timeout，由 JointQueryExecutor 启动时校验。</li>
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "data-query.joint")
public class DataQueryJointProperties {

    /** 请求体单键值数上限，与 JointQueryKeyValues.values 的 @Size(max = 50) 一致。 */
    static final int MAX_VALUES_IN_REQUEST = 50;

    /**
     * 入口值个数上限
     * <li>默认 20，1..50。</li>
     */
    private int maxEntryValues = 20;

    /**
     * 单键类型的值个数上限
     * <li>默认 50，1..50；请求超过返回 422，发现的键超过时截取前 N 个并记 MAX_VALUES_PER_KEY。</li>
     */
    private int maxValuesPerKey = 50;

    /**
     * 每个模板执行的行数上限
     * <li>默认 100；实际取 min(本值, 模板上限)，只取第一页。</li>
     */
    private int maxRowsPerTemplate = 100;

    /**
     * 单步模板执行数上限
     * <li>默认 20；超出的执行标 SKIPPED（STEP_EXECUTION_LIMIT）。</li>
     */
    private int maxExecutionsPerStep = 20;

    /**
     * 最大层数（页面累计）
     * <li>默认 6；请求 depth 不能超过本值 - 1。</li>
     */
    private int maxDepth = 6;

    /**
     * 一次联合查询的模板执行总数上限（页面累计）
     * <li>默认 60。</li>
     */
    private int maxTemplateExecutions = 60;

    /**
     * 一次联合查询的总时长上限秒数（页面累计）
     * <li>默认 60。</li>
     */
    private int maxTotalSeconds = 60;

    /**
     * 单步预算
     * <li>默认 8s，至少 1s：低于 BFF 9s 与 Gateway 10s；剩余不足 1 秒时不再启动新的模板执行（SKIPPED）。</li>
     */
    private Duration stepBudget = Duration.ofSeconds(8);

    /**
     * 单步并发上限
     * <li>默认 8；同一步内同时执行的模板数。</li>
     */
    private int stepConcurrency = 8;

    /**
     * 执行器线程数
     * <li>默认 16，所有请求共享；不小于 stepConcurrency。</li>
     */
    private int executorThreads = 16;

    /**
     * 执行器队列容量
     * <li>默认 64；满时拒绝，该执行标 SKIPPED（EXECUTOR_BUSY）。</li>
     */
    private int executorQueue = 64;

    /** 启动时拒绝非正值、超出请求体约束的值与小于单步并发的线程数。 */
    @AssertTrue(message = "data-query.joint 各项必须为正数，max-entry-values 与 max-values-per-key 不超过 50，"
        + "step-budget 至少 1s，executor-threads 不小于 step-concurrency")
    public boolean isJointLimitsValid() {
        return maxEntryValues > 0 && maxEntryValues <= MAX_VALUES_IN_REQUEST && maxValuesPerKey > 0
            && maxValuesPerKey <= MAX_VALUES_IN_REQUEST && maxRowsPerTemplate > 0 && maxExecutionsPerStep > 0
            && maxDepth > 0 && maxTemplateExecutions > 0 && maxTotalSeconds > 0 && stepBudget != null
            && stepBudget.toMillis() >= 1000 && stepConcurrency > 0 && executorThreads >= stepConcurrency
            && executorQueue > 0;
    }
}
