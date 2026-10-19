package com.paytech.ledger.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 追平时按序号升序读取的一页 account 入账批次。 */
@Getter
@AllArgsConstructor
public class AccountPostingPage {
    /** 本页已校验的批次，按序号升序。 */
    private final List<AccountPostingBatch> batches;

    /**
     * 下一页起始序号
     * <li>已读到流末尾时为空。</li>
     */
    private final Long nextFromSequence;
}
