package com.paytech.foundation.compensation.ext.job;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.compensation.api.CompensationScanResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * 将 XXL-Job 分片上下文转换为服务本地补偿扫描请求。
 *
 * @author Codex
 */
public final class CompensationEventXxlJobHandler {

    public static final String HANDLER_NAME = "compensationEventScan";

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationEventXxlJobHandler.class);

    private final CompensationEventScanner scanner;
    private final int batchSize;

    public CompensationEventXxlJobHandler(CompensationEventScanner scanner, int batchSize) {
        this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    /**
     * 执行一次有界扫描。Job 参数为空时扫描全部事件类型；非空时使用英文逗号分隔事件类型白名单。
     */
    @XxlJob(HANDLER_NAME)
    public void scan() {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardCount = XxlJobHelper.getShardTotal();
        if (shardCount <= 0 || shardIndex < 0 || shardIndex >= shardCount) {
            XxlJobHelper.handleFail("invalid XXL-Job shard context");
            return;
        }
        try {
            CompensationScanResult result = scan(XxlJobHelper.getJobParam(), shardIndex, shardCount);
            recordResult(result);
        } catch (RuntimeException exception) {
            recordFailure(exception);
        }
    }

    CompensationScanResult scan(String jobParameter, int shardIndex, int shardCount) {
        Set<String> eventTypes = parseEventTypes(jobParameter);
        return scanner.scan(new CompensationScanRequest(batchSize, shardIndex, shardCount, eventTypes));
    }

    private Set<String> parseEventTypes(String jobParameter) {
        if (jobParameter == null || jobParameter.isBlank()) {
            return Set.of();
        }
        Set<String> eventTypes = new LinkedHashSet<>();
        for (String eventType : jobParameter.split(",", -1)) {
            if (eventType.isBlank()) {
                throw new IllegalArgumentException("jobParameter contains a blank eventType");
            }
            eventTypes.add(eventType.trim());
        }
        return Set.copyOf(eventTypes);
    }

    private void recordResult(CompensationScanResult result) {
        String summary = "candidate=" + result.candidateCount() + ", submitted=" + result.submittedCount()
            + ", rejected=" + result.rejectedCount();
        XxlJobHelper.log("Compensation scan completed, {}", summary);
        if (result.rejectedCount() > 0) {
            XxlJobHelper.handleFail("compensation scan backpressure, " + summary);
            return;
        }
        XxlJobHelper.handleSuccess(summary);
    }

    private void recordFailure(RuntimeException exception) {
        String exceptionType = safeExceptionType(exception);
        LOGGER.error("Compensation scan failed, jobId={}, exceptionType={}", XxlJobHelper.getJobId(), exceptionType);
        XxlJobHelper.handleFail("compensation scan failed, exceptionType=" + exceptionType);
    }

    private String safeExceptionType(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();
        return simpleName.isBlank() ? "RuntimeException" : simpleName;
    }
}
