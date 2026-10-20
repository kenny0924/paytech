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
     * <li>只作为响应 asOf 回显；各卡片数量是 commercial 在调用时刻的状态计数。</li>
     */
    @NotNull(message = "统计时点不能为空")
    private final Instant asOf;
}
