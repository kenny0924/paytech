package com.paytech.channel.connector.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一轮渠道成本版本到期切换的结果。 */
@Getter
@AllArgsConstructor
public class ChannelCostActivationResult {
    /** 本轮切换为当前版本的 APPROVED 版本数。 */
    private final int activatedCount;

    /** 本轮发现时已不再是待切换状态（并发已处理）而跳过的版本数。 */
    private final int skippedCount;

    /** 本轮切换失败、保持 APPROVED 等下轮或人工处理的版本数。 */
    private final int failedCount;
}
