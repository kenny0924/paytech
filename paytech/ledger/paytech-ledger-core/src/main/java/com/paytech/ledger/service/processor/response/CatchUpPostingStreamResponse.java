package com.paytech.ledger.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 过账流追平结果。 */
@Getter
@AllArgsConstructor
public class CatchUpPostingStreamResponse {
    /** 本次过账或只推进进度的批次数 */
    private final int processedCount;

    /** 处理后该流的已消费序号；尚未消费任何批次时为 0 */
    private final long lastSequence;

    /** 是否已读到 account 流末尾 */
    private final boolean caughtUp;
}
