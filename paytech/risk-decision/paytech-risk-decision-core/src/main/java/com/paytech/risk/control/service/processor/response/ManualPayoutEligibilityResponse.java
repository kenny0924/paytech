package com.paytech.risk.control.service.processor.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次不可变资格评估的 Owner 结果。 */
@Getter
@AllArgsConstructor
public class ManualPayoutEligibilityResponse {
    /** 评估所属 Assessment ID。 */
    private final long assessmentId;

    /** 本次 Evaluation ID。 */
    private final long evaluationId;

    /** 风控决定 Code。 */
    private final String decision;

    /** 按 Code 排序的原因列表。 */
    private final List<String> reasonCodes;

    /** 命中的完整 Control 列表。 */
    private final List<String> matchedControls;

    /** Client 最低保证金要求；LOCAL_PARTNER 为空。 */
    private final String requiredAmount;

    /** 最低保证金币种；LOCAL_PARTNER 为空。 */
    private final String requiredCurrency;

    /** Control 集合摘要。 */
    private final String controlSetHash;
}
