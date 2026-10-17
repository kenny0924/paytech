package com.paytech.risk.decision.service.processor.request;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询规则集发布引用及独立于筛选的全量使用事实。 */
@Getter
@AllArgsConstructor
public class RuleSetReleaseReferencesRequest {

    /** 规则集整体根 ID。 */
    @Positive(message = "规则集 ID 必须为正整数")
    private final long ruleSetId;

    /** 页码，缺省为 1；范围由公共分页能力校验。 */
    private final Integer page;

    /** 每页条数，缺省为 10；可选值由公共分页能力校验。 */
    private final Integer size;

    /** 可选发布环境，未提供时查询所有环境。 */
    private final DecisionEnvironment environmentCode;

    /** 可选发布状态，未提供时查询所有状态。 */
    private final DecisionReleaseStatus status;

    /** 可选精确规则版本 ID，提供时必须属于所查规则集。 */
    @Positive(message = "规则版本 ID 必须为正整数")
    private final Long ruleSetVersionId;
}
