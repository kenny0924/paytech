package com.paytech.foundation.compensation.test.support;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.test.mapper.TestMapper;

/**
 * 验证异常重试分类的测试处理器。
 *
 * @author Codex
 */
public final class ExceptionClassificationCompensationHandler implements CompensationEventHandler {

    public static final String EVENT_TYPE = "ClassifyHandlerException";

    private static final String FAIL = "FAIL";
    private static final String ERROR = "ERROR";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String WRAPPED_FAIL = "WRAPPED_FAIL";

    private final TestMapper testMapper;

    public ExceptionClassificationCompensationHandler(TestMapper testMapper) {
        this.testMapper = Objects.requireNonNull(testMapper, "testMapper must not be null");
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(5), 3, Duration.ofMinutes(1), Duration.ofMinutes(1),
            1.0D, 0.0D);
    }

    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        String scenario = testMapper.findBusinessValue(event.eventKey(), event.subEventType());
        if (FAIL.equals(scenario)) {
            throw new FailException(CommonErrorCode.BUSINESS_CHECK_FAILED, "测试明确失败");
        }
        if (ERROR.equals(scenario)) {
            throw new ErrorException(CommonErrorCode.INTERNAL_ERROR, "测试结果未知");
        }
        if (WRAPPED_FAIL.equals(scenario)) {
            throw new CompletionException(new FailException(CommonErrorCode.BUSINESS_CHECK_FAILED, "测试包装后的明确失败"));
        }
        if (UNKNOWN.equals(scenario)) {
            throw new IllegalStateException("测试未分类异常");
        }
        return CompensationHandlerResult.success();
    }
}
