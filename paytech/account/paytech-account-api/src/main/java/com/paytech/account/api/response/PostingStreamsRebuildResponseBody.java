package com.paytech.account.api.response;

import lombok.Getter;
import lombok.Setter;

/** 按过账流重建结果；数值均为十进制字符串。 */
@Getter
@Setter
public class PostingStreamsRebuildResponseBody {
    /** 重算的 Owner 账户数。 */
    private String accountCount;

    /**
     * 重建前后余额有变化的 Owner 账户数
     * <li>借方合计、贷方合计或净额任一与流水重算值不同即计入；数据一致时为 0。</li>
     * <li>2026-10-30 新增的可选字段，旧调用方可忽略。</li>
     */
    private String changedAccountCount;

    /** 参与重算的流水数。 */
    private String entryCount;

    /** 重建时各流中最大的批次序号。 */
    private String maxPostingSequence;
}
