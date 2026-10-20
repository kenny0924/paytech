package com.paytech.ledger.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一轮到期规则版本生效的结果。 */
@Getter
@AllArgsConstructor
public class ActivateDueRuleVersionsResponse {
    /** 本轮切换为 ACTIVE 的版本数 */
    private final int activatedCount;

    /** 本轮因已被当前版本取代而置为 RETIRED（不生效）的版本数 */
    private final int retiredCount;

    /** 本轮处理失败、保持 APPROVED 待下一轮重试的版本数 */
    private final int failedCount;
}
