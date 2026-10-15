package com.paytech.finance.accounting.domain.view;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.finance.accounting.core.enums.TrialBalanceStatus;

import lombok.Getter;
import lombok.Setter;

/** 试算运行摘要：运行头与行数、异常数。 */
@Getter
@Setter
public class TrialBalanceRunView {
    /** 试算运行主键。 */
    private Long trialBalanceRunId;

    /** 账簿主键。 */
    private Long ledgerBookId;

    /** 会计期间主键。 */
    private Long periodId;

    /** 账簿期间内试算序号。 */
    private Integer runNo;

    /** 纳入的最大分录序号。 */
    private Long asOfSequence;

    /** 试算状态。 */
    private TrialBalanceStatus status;

    /** 期末余额实际借方合计，按币种精度规范。 */
    private BigDecimal totalDebit;

    /** 期末余额实际贷方合计，按币种精度规范。 */
    private BigDecimal totalCredit;

    /** 终态内容摘要。 */
    private String contentHash;

    /** 试算行数。 */
    private Integer lineCount;

    /** 异常数。 */
    private Integer exceptionCount;

    /** 创建时间。 */
    private Instant createTime;
}
