package com.paytech.account.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 主体与币种下的事实应用进度与各过账流最大序号
 * <li>ledger 关账：appliedFactCount / maxAppliedFactId 与 billing publishedFactCount / maxPublishedFactId 比对；每条流的
 * maxPostingSequence 与 ledger 已消费版本比对。</li>
 */
@Getter
@Setter
public class AccountPostingProgressResponseBody {
    /** 查询的主体类型。 */
    private String entityType;

    /** 查询的主体标识。 */
    private String entityId;

    /** 查询的币种。 */
    private String currency;

    /**
     * 已应用计费事实数，十进制字符串
     * <li>账簿主体为该主体的事实中自身事件已到达的事实（APPLIED、REVERSED、SKIPPED），排除仅因替代先到而占位的 PENDING_SUPERSEDED。</li>
     */
    private String appliedFactCount;

    /** 已应用事实中最大的 factId；尚无时为空。 */
    private String maxAppliedFactId;

    /** 该账簿涉及的全部过账流，按主体类型、主体标识、分片号排序；没有流时为空列表。 */
    private List<Stream> streams;

    /** 一条过账流的进度。 */
    @Getter
    @Setter
    public static class Stream {
        /** 流主体类型。 */
        private String entityType;

        /** 流主体标识。 */
        private String entityId;

        /** 流币种。 */
        private String currency;

        /** 流分片号，十进制字符串。 */
        private String shardNo;

        /** 流内最大批次序号（last_sequence），十进制字符串。 */
        private String maxPostingSequence;

        /** 最后一个批次的生成时间（UTC）。 */
        private Instant lastPostedTime;
    }
}
