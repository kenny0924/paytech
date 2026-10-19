package com.paytech.account.api.response;

import lombok.Getter;
import lombok.Setter;

/** 按过账流重建结果；数值均为十进制字符串。 */
@Getter
@Setter
public class PostingStreamsRebuildResponseBody {
    /** 重算的 Owner 账户数。 */
    private String accountCount;

    /** 参与重算的流水数。 */
    private String entryCount;

    /** 重建时各流中最大的批次序号。 */
    private String maxPostingSequence;
}
