package com.paytech.operations.console.bff.service.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.*;
import java.util.function.Supplier;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.ClientReviewCctClient;
import com.paytech.client.contract.api.onboarding.response.ClassificationSetResponseBody;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.integration.PlatformReferenceDataClient;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ReviewCaseDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.IndustryCategoryResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.IndustryResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.MccResponseBody;
import com.paytech.operations.console.bff.service.processor.request.ClientReviewDetailsRequest;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import lombok.AllArgsConstructor;

/**
 * O02 审核详情：先读 Risk 任务（审核事实的 Owner），再并发读取 CCT 申请资料与分类、Platform ACTIVE 行业，最后按编码读取涉及的 MCC
 * <li>Risk 失败整页失败；CCT 或 Platform 某部分失败或超时只把该部分标为 UNAVAILABLE，不伪造空数据。</li>
 * <li>行业组装为大类 → 细类的树；MCC 为当前分类与细类建议 MCC 的编码，分类或行业不可用时只取可得部分。</li>
 * <li>各部分共享 3 秒总等待上限；子线程沿用当前请求 ID。</li>
 * <li>操作者会话或权限失效属于整页失败，不降级。</li>
 */
@Component
@AllArgsConstructor
public class ClientReviewDetailsProcessor
    extends AbstractProcessor<ClientReviewDetailsRequest, ReviewCaseDetailResponseBody> {
    /** CCT 与 Platform 各部分的总等待上限。 */
    static final long DEADLINE_SECONDS = 3;

    private final ClientReviewCctClient cct;
    private final ClientReviewRiskClient risk;
    private final PlatformReferenceDataClient referenceData;

    @Override
    protected ReviewCaseDetailResponseBody processRequest(ClientReviewDetailsRequest request) {
        var operator = request.getOperator();
        var review = risk.detail(operator, request.getCaseId());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
            var application = executor.submit(withRequestContext(() -> cct.application(operator, request.getCaseId())));
            var classification =
                executor.submit(withRequestContext(() -> cct.classifications(operator, request.getCaseId())));
            var industries = executor.submit(withRequestContext(
                () -> categories(referenceData.industries(ReferenceDataStatus.ACTIVE.getCode()).getItems())));
            var applicationValue = result(application, deadline);
            var classificationValue = result(classification, deadline);
            var industriesValue = result(industries, deadline);
            var codes = mccCodes(classificationValue, industriesValue);
            var mccs = executor.submit(withRequestContext(() -> referenceData.mccsByCodes(codes)));
            var mccsValue = result(mccs, deadline);
            return new ReviewCaseDetailResponseBody(review, status(applicationValue), applicationValue,
                status(classificationValue), classificationValue, status(industriesValue),
                industriesValue == null ? List.of() : industriesValue, status(mccsValue), mccsValue == null ? List.of() : mccsValue);
        }
    }

    /** 平铺的行业组装为大类 → 细类；细类按排序号，没有上级大类的细类不展示。 */
    static List<IndustryCategoryResponseBody> categories(List<IndustryResponseBody> industries) {
        Map<String, IndustryCategoryResponseBody> categories = new LinkedHashMap<>();
        industries.stream().filter(item -> Integer.valueOf(1).equals(item.getLevel()))
            .sorted(Comparator.comparing(IndustryResponseBody::getSortNo, Comparator.nullsLast(Integer::compareTo)))
            .forEach(item -> {
                var category = new IndustryCategoryResponseBody();
                category.setIndustryCode(item.getIndustryCode());
                category.setName(item.getName());
                category.setLogoPublicUrl(item.getLogoPublicUrl());
                category.setChildren(new ArrayList<>());
                categories.put(item.getIndustryCode(), category);
            });
        industries.stream().filter(item -> Integer.valueOf(2).equals(item.getLevel()))
            .sorted(Comparator.comparing(IndustryResponseBody::getSortNo, Comparator.nullsLast(Integer::compareTo)))
            .forEach(item -> {
                var category = categories.get(item.getParentIndustryCode());
                if (category != null) {
                    category.getChildren().add(item);
                }
            });
        return List.copyOf(categories.values());
    }

    /** 当前分类与各细类建议 MCC 的编码，去重升序。 */
    static List<String> mccCodes(ClassificationSetResponseBody classification, List<IndustryCategoryResponseBody> industries) {
        var codes = new TreeSet<String>();
        if (classification != null) {
            classification.getItems().forEach(item -> codes.add(item.getMccCode()));
        }
        if (industries != null) {
            industries.forEach(category -> category.getChildren().stream().map(IndustryResponseBody::getDefaultMccCode)
                .filter(Objects::nonNull).forEach(codes::add));
        }
        return List.copyOf(codes);
    }

    /** 可用性标记。 */
    static String status(Object value) {
        return value == null ? "UNAVAILABLE" : "AVAILABLE";
    }

    /** 子线程沿用当前请求 ID，便于下游日志关联。 */
    static <T> Callable<T> withRequestContext(Supplier<T> call) {
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
