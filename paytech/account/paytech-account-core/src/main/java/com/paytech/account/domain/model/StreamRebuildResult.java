package com.paytech.account.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按入账流重建主体与币种下 Owner 账户余额的结果。 */
@Getter
@AllArgsConstructor
public class StreamRebuildResult {
    /** 重算的 Owner 账户数。 */
    private final int accountCount;

    /** 重算前后借方合计、贷方合计或净额有任一变化的账户数。 */
    private final int changedAccountCount;

    /** 参与重算的流水数。 */
    private final long entryCount;

    /** 重建时各流中最大的批次序号。 */
    private final long maxPostingSequence;
}
