package com.paytech.operations.console.bff.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.operations.console.bff.integration.ClientReviewCctClient;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ReviewCaseDetailResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.enums.OperationsErrorCode;

import lombok.AllArgsConstructor;

/**
 * O02 审核详情：先读 Risk 任务（审核事实的 Owner），再并发读取 CCT 申请资料、分类与行业目录
 * <li>Risk 失败整页失败；CCT 某部分失败只把该部分标为 UNAVAILABLE，不伪造空数据。</li>
 * <li>操作者会话或权限失效属于整页失败，不降级。</li>
 */
@Component
@AllArgsConstructor
public class ClientReviewDetailsProcessor {
    /** CCT 各部分的总等待上限。 */
    static final long DEADLINE_SECONDS = 3;

    private final ClientReviewRiskClient risk;
    private final ClientReviewCctClient cct;

    /** 聚合审核详情。 */
    public ReviewCaseDetailResponseBody details(OperatorContext operator, String caseId) {
        var review = risk.detail(operator, caseId);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
            var application = executor.submit(withRequestContext(() -> cct.application(operator, caseId)));
            var classification = executor.submit(withRequestContext(() -> cct.classifications(operator, caseId)));
            var options = executor.submit(withRequestContext(() -> cct.options(operator)));
            var applicationValue = result(application, deadline);
            var classificationValue = result(classification, deadline);
            var optionsValue = result(options, deadline);
            return new ReviewCaseDetailResponseBody(review, status(applicationValue), applicationValue, status(classificationValue),
                classificationValue, status(optionsValue), optionsValue == null ? List.of() : optionsValue.getIndustries());
        }
    }

    /** 可用性标记。 */
    static String status(Object value) {
        return value == null ? "UNAVAILABLE" : "AVAILABLE";
    }

    /** 子线程沿用当前请求 ID，便于下游日志关联。 */
    static <T> java.util.concurrent.Callable<T> withRequestContext(Supplier<T> call) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                return call.get();
            } finally {
                MDC.clear();
            }
        };
    }

    /** 部分失败返回 null；操作者会话或权限失效直接抛出。 */
    static <T> T result(Future<T> future, long deadline) {
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException();
        } catch (TimeoutException timeout) {
            future.cancel(true);
            return null;
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof FailException rejected && isOperatorFailure(rejected)) {
                throw rejected;
            }
            return null;
        }
    }

    /** 会话或权限类错误不能降级为部分不可用。 */
    static boolean isOperatorFailure(FailException failure) {
        return OperationsErrorCode.OPERATIONS_SESSION_INVALID.getCode().equals(failure.getCode())
            || OperationsErrorCode.OPERATIONS_PERMISSION_DENIED.getCode().equals(failure.getCode());
    }
}
