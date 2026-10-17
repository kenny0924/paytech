package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询规则集固定版本上界内的历史分页。 */
@Getter
@AllArgsConstructor
public class RuleSetHistoryRequest {

    /** 规则集整体根 ID。 */
    @Positive(message = "规则集 ID 必须为正整数")
    private final long ruleSetId;

    /**
     * 本次浏览的业务版本上界。
     * <li>必填，且不得超过查询时的当前业务版本。</li>
     */
    @NotNull(message = "历史版本上界必填")
    @Positive(message = "历史版本上界必须为正整数")
    private final Integer maxVersionNo;

    /** 页码，缺省为 1；范围由公共分页能力校验。 */
    private final Integer page;

    /** 每页条数，缺省为 10；可选值由公共分页能力校验。 */
    private final Integer size;
}
