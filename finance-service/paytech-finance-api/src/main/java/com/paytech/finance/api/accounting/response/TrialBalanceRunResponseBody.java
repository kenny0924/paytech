package com.paytech.finance.api.accounting.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 试算运行摘要。 */
@Getter
@Setter
public class TrialBalanceRunResponseBody {
    /** 试算运行主键。 */
    private String trialBalanceRunId;

    /** 账簿主键。 */
    private String ledgerBookId;

    /** 会计期间主键。 */
    private String periodId;

    /** 账簿期间内从 1 连续递增的试算序号。 */
    private String runNo;

    /** 本次试算纳入的最大分录序号。 */
    private String asOfSequence;

    /**
     * 试算状态
     * <li>SUCCEEDED：借贷相等且无异常；FAILED：存在异常或借贷不等。</li>
     */
    private String status;

    /** 期末余额处于实际借方的合计，主货币单位，按账簿币种精度补齐；不是本期发生额。 */
    private String totalDebit;

    /** 期末余额处于实际贷方的合计，主货币单位，按账簿币种精度补齐；不是本期发生额。 */
    private String totalCredit;

    /** 终态冻结的内容摘要（CODE-MOR:FINANCE:TRIAL_BALANCE:1，SHA-256 小写十六进制）。 */
    private String contentHash;

    /** 试算行数。 */
    private Integer lineCount;

    /** 异常数。 */
    private Integer exceptionCount;

    /** 创建时间（UTC）。 */
    private Instant createTime;
}
