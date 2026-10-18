package com.paytech.ledger.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 试算详情：摘要、按科目代码排序的试算行与异常。 */
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

    /** 按科目代码排序的试算行。 */
    private List<Line> lines;

    /** 异常发现事实。 */
    private List<Finding> exceptions;

    /** 试算行：一个可过账科目在账簿内的汇总。 */
    @Getter
    @Setter
    public static class Line {
        /** 会计科目主键。 */
        private String chartAccountId;

        /** 科目代码。 */
        private String accountCode;

        /** 科目名称。 */
        private String accountName;

        /** 科目类别：ASSET、LIABILITY、EQUITY、REVENUE、EXPENSE。 */
        private String accountClass;

        /** 科目正常方向：DEBIT 或 CREDIT。 */
        private String normalSide;

        /**
         * 期初余额
         * <li>按正常方向有符号，负数表示反向余额。</li>
         */
        private String openingBalance;

        /** 本期借方发生额。 */
        private String periodDebit;

        /** 本期贷方发生额。 */
        private String periodCredit;

        /**
         * 期末余额
         * <li>期初加本期正常方向净额，按正常方向有符号。</li>
         */
        private String closingBalance;
    }

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
