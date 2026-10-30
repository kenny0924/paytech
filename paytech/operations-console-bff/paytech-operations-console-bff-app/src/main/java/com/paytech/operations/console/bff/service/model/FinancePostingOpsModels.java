package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 明细账运维页面模型：账簿入账进度、过账流批次浏览、ledger 追平与 account 余额重建
 * <li>进度字段与 ledger PostingProgressQueryApi 合同一一对应，由 Client 适配器严格转换；BFF 只为落后的流补充"卡住的批次"。</li>
 * <li>批次摘要由 account 批次裁剪而来：不含流水明细，只带流水条数；点开批次走已有的批次详情路由。</li>
 * <li>ID、计数与序号为十进制字符串，时间为 UTC。</li>
 *
 * @author Claude
 */
public final class FinancePostingOpsModels {
    /** 过账流身份 {@code {OE|FE}.{entityId}.{currency}.{shardNo}}；再由 account-api 的解析器校验并拆分。 */
    public static final String STREAM_ID_PATTERN = "(OE|FE)\\.[A-Z0-9]{1,19}\\.[A-Z]{3}\\.(0|[1-9][0-9]{0,8})";

    /** 只承载类型，禁止创建实例。 */
    private FinancePostingOpsModels() {
    }

    /** 账簿入账进度（B1）。 */
    @Getter
    @Setter
    public static class PostingProgressResponseBody {
        /** 账簿主键。 */
        private String ledgerBookId;

        /** 账簿主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
        private String entityType;

        /** 账簿主体标识。 */
        private String entityId;

        /** 账簿币种。 */
        private String currency;

        /** billing → account 计费事实段。 */
        private FactSegment factSegment;

        /** account → ledger 过账流段。 */
        private StreamSegment streamSegment;
    }

    /** billing → account 计费事实段；status 为 CONSISTENT、BEHIND 或 UNAVAILABLE，UNAVAILABLE 时数字为空。 */
    @Getter
    @Setter
    public static class FactSegment {
        /** 段判定。 */
        private String status;

        /** billing 已批准未发布的事实数。 */
        private String billingPendingCount;

        /** billing 未发布事实中最大的 factId；没有时为空。 */
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

    /** account → ledger 过账流段；段判定取各流中最差的一个。 */
    @Getter
    @Setter
    public static class StreamSegment {
        /** 段判定：CONSISTENT、BEHIND 或 UNAVAILABLE。 */
        private String status;

        /** 账簿涉及的全部过账流；account 不可用时为空列表。 */
        private List<Stream> streams;
    }

    /** 一条过账流的两侧进度。 */
    @Getter
    @Setter
    public static class Stream {
        /** 流身份。 */
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

        /** account 最后一个批次的生成时间；未给出时为空。 */
        private Instant accountLastPostedTime;

        /** ledger 已消费序号；从未消费时为 0。 */
        private String ledgerConsumedSequence;

        /** 落后批次数。 */
        private String lag;

        /** 流判定：CONSISTENT 或 BEHIND。 */
        private String status;

        /**
         * 卡住的批次：ledger 已消费序号 + 1 对应的 account 批次（BFF 补充）
         * <li>只有 BEHIND 的流才读取；不落后、读取失败或 account 尚无该批次时为空。</li>
         */
        private PostingSummary stuckPosting;
    }

    /** 过账批次摘要：卡住的批次与批次浏览共用，不含流水明细。 */
    @Getter
    @Setter
    public static class PostingSummary {
        /** 批次主键。 */
        private String postingId;

        /** 流内批次序号。 */
        private String postingSequence;

        /** 批次来源：ECONOMIC_FACT 或 BALANCE_MOVEMENT。 */
        private String sourceType;

        /** 事实批次的计费事实主键；桶移动批次为空。 */
        private String factId;

        /** 事实批次的事实类型代码；桶移动批次为空。 */
        private String factType;

        /** 事实批次的应用结果：APPLIED 或 SKIPPED；桶移动批次为空。 */
        private String factApplyStatus;

        /** 批次生成的 UTC 时间。 */
        private Instant postedTime;

        /** AccountPosted 收到 Broker 确认的 UTC 时间；为空表示 account 尚未发出 AccountPosted（追平直接读 account，同样能补上）。 */
        private Instant publishedTime;

        /** 批次内流水条数。 */
        private String entryCount;
    }

    /** 一页过账批次（B2），按序号升序。 */
    @Getter
    @Setter
    public static class PostingListResponseBody {
        /** 批次摘要。 */
        private List<PostingSummary> items;

        /** 下一页起始序号；已到流末尾时为空。 */
        private String nextFromSequence;
    }

    /** ledger 追平结果（B3）。 */
    @Getter
    @Setter
    public static class CatchUpPostingStreamResponseBody {
        /** 本次处理的批次数。 */
        private String processedCount;

        /** 处理后该流的已消费序号。 */
        private String lastSequence;

        /** 是否已追平；false 表示达到本次批次上限，可再次追平。 */
        private boolean caughtUp;
    }

    /** 余额重建请求（B4）。 */
    @Getter
    @Setter
    public static class RebuildPostingStreamsRequestBody {
        /** 主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
        @NotBlank(message = "主体类型必填")
        @Pattern(regexp = FinanceSubledgerModels.ENTITY_TYPE_PATTERN, message = "主体类型只能为 OPERATING_ENTITY 或 FINANCE_ENTITY")
        private String entityType;

        /** 主体标识：OE 为 entityNo，Finance Entity 为十进制主键。 */
        @NotBlank(message = "主体标识必填")
        @Pattern(regexp = "[A-Z0-9]{1,19}", message = "主体标识格式不正确")
        private String entityId;

        /** 大写三字母币种。 */
        @NotBlank(message = "币种必填")
        @Pattern(regexp = "[A-Z]{3}", message = "币种格式不正确")
        private String currency;
    }

    /** 余额重建结果（B4）。 */
    @Getter
    @Setter
    public static class RebuildPostingStreamsResponseBody {
        /** 重算的 Owner 账户数。 */
        private String accountCount;

        /** 余额有变化（已被纠正）的 Owner 账户数。 */
        private String changedAccountCount;

        /** 参与重算的流水数。 */
        private String entryCount;

        /** 各流中最大的批次序号。 */
        private String maxPostingSequence;
    }
}
