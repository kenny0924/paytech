package com.paytech.operations.console.bff.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 商务工作台指标的内部请求。 */
@Getter
@AllArgsConstructor
public class CommercialDashboardRequest {

    /**
     * 统计时点
     * <li>"已排期待生效"按该时点之后生效计数。</li>
     */
    @NotNull(message = "统计时点不能为空")
    private final Instant asOf;
}
