package com.paytech.ledger.domain;

import java.util.List;

import com.paytech.ledger.api.enums.PostingProgressStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一本账簿的两段入账进度判定结果；期间关账步骤 3 与账簿入账进度查询共用
 * <li>段 ① billing → account：billing 无未发布事实，account 已应用事实数与最大 factId 等于 billing 已发布事实数与最大 factId。</li>
 * <li>段 ② account → ledger：account 列出的每条过账流，ledger 已消费序号等于 account 最大序号；段判定取各流中最差的一个。</li>
 */
@Getter
@AllArgsConstructor
public class BookPostingProgress {
    /** 账簿主键。 */
    private final long ledgerBookId;

    /** 账簿主体类型。 */
    private final LedgerEntityType entityType;

    /** 账簿主体标识。 */
    private final String entityId;

    /** 账簿币种。 */
    private final String currency;

    /** 段 ① 判定；billing 或 account 不可用时为 UNAVAILABLE。 */
    private final PostingProgressStatus factStatus;

    /**
     * billing 进度
     * <li>billing 不可用，或段 ① 因 account 不可用而为 UNAVAILABLE 时为空。</li>
     */
    private final BillingFactProgress billing;

    /**
     * account 进度
     * <li>account 不可用，或段 ① 因 billing 不可用而为 UNAVAILABLE 时为空；流清单另见 {@link #streams}。</li>
     */
    private final AccountPostingProgress account;

    /** 段 ② 判定；account 不可用时为 UNAVAILABLE。 */
    private final PostingProgressStatus streamStatus;

    /** 段 ② 的各条流，顺序同 account 进度；account 不可用时为空列表。 */
    private final List<StreamProgress> streams;

    /** 两段都已追平（关账步骤 3 对该账簿的判定"通过"）。 */
    public boolean isCaughtUp() {
        return factStatus == PostingProgressStatus.CONSISTENT && streamStatus == PostingProgressStatus.CONSISTENT;
    }

    /** 一条过账流的两侧进度。 */
    @Getter
    @AllArgsConstructor
    public static class StreamProgress {
        /** account 侧的流身份、最大序号与最后入账时间。 */
        private final AccountPostingProgress.Stream accountStream;

        /** ledger 已消费序号；从未消费时为 0。 */
        private final long consumedSequence;

        /** 流判定：已消费序号等于 account 最大序号为 CONSISTENT，否则 BEHIND。 */
        private final PostingProgressStatus status;

        /** 落后批次数：account 最大序号减 ledger 已消费序号。 */
        public long lag() {
            return accountStream.getMaxPostingSequence() - consumedSequence;
        }
    }
}
