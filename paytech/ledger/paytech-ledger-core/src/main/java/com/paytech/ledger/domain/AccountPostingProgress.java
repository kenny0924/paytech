package com.paytech.ledger.domain;

import java.util.List;

import com.paytech.foundation.messaging.api.AggregateKey;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * account 对一个账簿主体报告的入账进度；关账步骤 3 两段检查使用
 * <li>事实计数只计自身事件已到达的事实；流清单覆盖该账簿涉及的全部过账流（Finance Entity 账簿含引用它的 OE 流）。</li>
 */
@Getter
@AllArgsConstructor
public class AccountPostingProgress {
    /** account 已应用的事实数。 */
    private final long appliedFactCount;

    /**
     * account 已应用的最大 factId
     * <li>尚未应用任何事实时为空。</li>
     */
    private final Long maxAppliedFactId;

    /** 该账簿涉及的全部过账流。 */
    private final List<Stream> streams;

    /** 一条过账流及其当前最大批次序号。 */
    @Getter
    @AllArgsConstructor
    public static class Stream {
        /** 过账流的聚合身份，即 ledger 消费进度键。 */
        private final AggregateKey aggregateKey;

        /** account 已生成的最大批次序号。 */
        private final long maxPostingSequence;
    }
}
