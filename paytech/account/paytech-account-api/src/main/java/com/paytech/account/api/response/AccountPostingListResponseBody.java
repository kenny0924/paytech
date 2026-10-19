package com.paytech.account.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一页过账批次（ledger 追平读取）。 */
@Getter
@Setter
public class AccountPostingListResponseBody {
    /** 按序号升序的批次，每个含全部流水。 */
    private List<AccountPostingResponseBody> items;

    /**
     * 下一页起始序号，十进制字符串
     * <li>本页满 limit 时为最后一个批次序号 + 1；否则为空，表示已读到流末尾。</li>
     */
    private String nextFromSequence;
}
