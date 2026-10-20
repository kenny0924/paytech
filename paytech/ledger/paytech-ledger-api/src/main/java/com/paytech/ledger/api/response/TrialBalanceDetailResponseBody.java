package com.paytech.ledger.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 试算详情：摘要、按科目编号排序的试算行、级次汇总行与异常。 */
@Getter
@Setter
public class TrialBalanceDetailResponseBody {
    /** 试算运行主键。 */
    private String trialBalanceRunId;

    /** 账簿主键。 */
    private String ledgerBookId;

    /** 会计期间主键。 */
    private String periodId;

    /** 试算序号。 */
    private String runNo;

    /** 纳入的最大分录序号。 */
    private String asOfSequence;

    /** 试算状态：SUCCEEDED 或 FAILED。 */
    private String status;

    /** 期末余额实际借方合计。 */
    private String totalDebit;

    /** 期末余额实际贷方合计。 */
    private String totalCredit;

    /** 内容摘要。 */
    private String contentHash;

    /** 试算行数。 */
    private Integer lineCount;

    /** 异常数。 */
    private Integer exceptionCount;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /**
     * 按科目编号排序的试算行
     * <li>末级可过账科目，与内容摘要覆盖的持久化行一致。</li>
     */
    private List<AccountBalanceLine> lines;

    /**
     * 按科目编号排序的级次汇总行
     * <li>查询时由科目树向上汇总，postingAllowed 为 false；任一汇总行的借、贷与余额等于其直接下级之和；不进入内容摘要。</li>
     */
    private List<AccountBalanceLine> summaryLines;

    /** 异常发现事实。 */
    private List<Finding> exceptions;

    /** 异常发现事实。 */
    @Getter
    @Setter
    public static class Finding {
        /** 异常分类，本期只产生 GENERAL_LEDGER_MISMATCH。 */
        private String exceptionType;

        /** 引用类型：ACCOUNT（账户投影不一致）或 CHART_ACCOUNT（借贷合计不等，引用科目表）。 */
        private String referenceType;

        /** 引用对象主键。 */
        private String referenceId;

        /** 绝对差异金额。 */
        private String differenceAmount;

        /** 处置状态，本期均为 OPEN。 */
        private String status;
    }
}
