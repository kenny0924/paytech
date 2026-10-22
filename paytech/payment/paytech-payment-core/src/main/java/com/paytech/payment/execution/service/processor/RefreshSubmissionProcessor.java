package com.paytech.payment.execution.service.processor;

import java.time.Duration;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.service.processor.request.ConfirmPayResultRequest;
import com.paytech.payment.execution.service.processor.request.GetSubmissionRequest;
import com.paytech.payment.execution.service.processor.response.GetSubmissionResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 刷新来源提交的支付结果（3DS 全链路 technique 3.3.1）：收银台轮询发现仍在验证或确认中时，立即触发一次“确认支付结果”，不必等查单恢复任务的退避周期。
 *
 * <p>
 * 先按 EX-I06 读取（同样的归属与 404 规则）；尝试处理中且 Redis 键 {@value #RATE_KEY_PREFIX}{attemptId}（SET NX，TTL 5 秒）获取成功时调用
 * {@link ConfirmPayResultProcessor}，确认后重新读取当前事实返回。刷新只是加速：Redis 不可用、Intent 锁冲突时跳过确认，直接返回当前事实；
 * 正确性由查单恢复任务保证。不返回 3DS 跳转地址。
 */
@Slf4j
@Component
@AllArgsConstructor
public class RefreshSubmissionProcessor extends AbstractProcessor<GetSubmissionRequest, GetSubmissionResponse> {
    /** 刷新限频键前缀，键后接支付尝试 ID。 */
    static final String RATE_KEY_PREFIX = "pay-result-refresh:";

    /** 同一尝试两次确认之间的最短间隔。 */
    static final Duration RATE_WINDOW = Duration.ofSeconds(5);

    final RedissonClient redissonClient;
    final GetSubmissionProcessor getSubmissionProcessor;
    final ConfirmPayResultProcessor confirmPayResultProcessor;

    @Override
    protected GetSubmissionResponse processRequest(GetSubmissionRequest request) {
        // 读取当前事实并校验归属
        GetSubmissionResponse current = getSubmissionProcessor.process(request);
        if (current.getStatus() != AttemptStatus.PROCESSING || !acquireRefreshSlot(current.getAttemptId())) {
            return current;
        }

        // 确认支付结果
        if (!confirm(current)) {
            return current;
        }

        // 读取确认后的事实
        return getSubmissionProcessor.process(request);
    }

    /** 5 秒内同一尝试只放行一次；Redis 不可用时不放行。 */
    boolean acquireRefreshSlot(long attemptId) {
        try {
            return redissonClient.getBucket(RATE_KEY_PREFIX + attemptId).setIfAbsent("1", RATE_WINDOW);
        } catch (RuntimeException unavailable) {
            log.warn("刷新限频不可用，跳过确认: attemptId={}, failure={}", attemptId, unavailable.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 调用确认支付结果
     *
     * @return 是否执行了确认；Intent 正被其他请求处理或依赖不可用时为 false
     */
    boolean confirm(GetSubmissionResponse current) {
        try {
            confirmPayResultProcessor.process(new ConfirmPayResultRequest(current.getIntentId(), current.getAttemptId()));
            return true;
        } catch (ConcurrentRequestException | ServiceUnavailableException busy) {
            log.info("确认支付结果跳过，返回当前事实: attemptId={}, failure={}", current.getAttemptId(),
                busy.getClass().getSimpleName());
            return false;
        }
    }
}
