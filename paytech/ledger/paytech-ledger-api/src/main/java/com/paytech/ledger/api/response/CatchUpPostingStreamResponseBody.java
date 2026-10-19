package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 过账流追平结果。 */
@Getter
@Setter
public class CatchUpPostingStreamResponseBody {
    /** 本次过账（或按 BALANCE_MOVEMENT / SKIPPED 只推进进度）的批次数，十进制字符串。 */
    private String processedCount;

    /**
     * 处理后该流的已消费序号，十进制字符串
     * <li>尚未消费任何批次时为 0。</li>
     */
    private String lastSequence;

    /** 是否已读到 account 流末尾；false 表示达到 maxBatches，可再次调用继续。 */
    private boolean caughtUp;
}
