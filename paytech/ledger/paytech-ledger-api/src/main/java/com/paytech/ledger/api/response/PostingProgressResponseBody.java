package com.paytech.ledger.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 账簿入账进度；ID、计数与序号为十进制字符串，时间为 UTC
 * <li>status 取值见 {@code PostingProgressStatus}：CONSISTENT、BEHIND、UNAVAILABLE。</li>
 */
@Getter
@Setter
public class PostingProgressResponseBody {
    /** 账簿主键。 */
    private String ledgerBookId;

    /** 账簿主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
    private String entityType;

    /** 账簿主体标识：OE 为 entityNo，Finance Entity 为十进制主键。 */
    private String entityId;

    /** 账簿币种。 */
    private String currency;

    /** billing → account 计费事实段。 */
    private FactSegment factSegment;

    /** account → ledger 过账流段。 */
    private StreamSegment streamSegment;

    /** billing → account 计费事实段；判定：billing 无未发布事实，且两边事实数与最大 factId 相同。 */
    @Getter
    @Setter
    public static class FactSegment {
        /** 段判定。 */
        private String status;

        /**
         * billing 已批准未发布的事实数
         * <li>UNAVAILABLE 时为空（下列数字同）。</li>
         */
        private String billingPendingCount;

        /** billing 未发布事实中最大的 factId；没有未发布事实时为空。 */
        private String billingMaxPendingFactId;

        /** billing 已确认发布的事实数。 */
        private String billingPublishedCount;

        /** billing 已确认发布的最大 factId；尚无时为空。 */
        private String billingMaxPublishedFactId;

        /** account 已应用的事实数。 */
        private String accountAppliedCount;

        /** account 已应用的最大 factId；尚无时为空。 */
        private String accountMaxAppliedFactId;
    }

    /** account → ledger 过账流段；段判定取各流中最差的一个，没有流时为 CONSISTENT。 */
    @Getter
    @Setter
    public static class StreamSegment {
        /** 段判定。 */
        private String status;

        /**
         * 该账簿涉及的全部过账流，顺序同 account 进度
         * <li>Finance Entity 账簿同时包含其事实所在的 OE 流；account 不可用时为空列表。</li>
         */
        private List<Stream> streams;
    }

    /** 一条过账流的两侧进度。 */
    @Getter
    @Setter
    public static class Stream {
        /** 流身份 {@code {OE|FE}.{entityId}.{currency}.{shardNo}}，即 ledger 消费进度键。 */
        private String streamId;

        /** 流主体类型。 */
        private String entityType;

        /** 流主体标识。 */
        private String entityId;

        /** 流币种。 */
        private String currency;

        /** 流分片号。 */
        private String shardNo;

        /** account 已生成的最大批次序号。 */
        private String accountMaxSequence;

        /** account 最后一个批次的生成时间；account 未给出时为空。 */
        private Instant accountLastPostedTime;

        /** ledger 已消费序号；从未消费时为 0。 */
        private String ledgerConsumedSequence;

        /** 落后批次数：account 最大序号减 ledger 已消费序号。 */
        private String lag;

        /** 流判定：已消费序号等于 account 最大序号为 CONSISTENT，否则 BEHIND。 */
        private String status;
    }
}
