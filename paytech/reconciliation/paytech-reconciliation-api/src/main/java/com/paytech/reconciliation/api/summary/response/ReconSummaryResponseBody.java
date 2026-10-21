package com.paytech.reconciliation.api.summary.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 对账在途汇总：三块，金额按币种精度补齐。 */
@Getter
@Setter
public class ReconSummaryResponseBody {
    /** 经营主体编号；汇总全部时为空 */
    private String entityNo;

    /** 币种 */
    private String currency;

    /** 在途渠道账单：MATCHING（未对平）与 RECONCILED（已对平未到账） */
    private InTransitStatements inTransitStatements;

    /** 开放异常（OPEN、UNDER_REVIEW）按账龄段，段固定为 D0_3、D4_7、D8_30、D31_PLUS 且按此顺序 */
    private List<ExceptionAgeBucket> openExceptionsByAge;

    /** 未匹配银行流水（UNMATCHED、PARTIALLY_MATCHED、EXCEPTION） */
    private UnmatchedBankLines unmatchedBankLines;

    /** 在途渠道账单。 */
    @Getter
    @Setter
    public static class InTransitStatements {
        /** MATCHING 账单数 */
        private Integer matchingCount;

        /** MATCHING 账单净额合计（带符号：CREDIT 为正、DEBIT 为负） */
        private String matchingNetAmount;

        /** RECONCILED 账单数 */
        private Integer reconciledCount;

        /** RECONCILED 账单尚未银行确认的净额合计（带符号：净额减已确认金额） */
        private String reconciledUnconfirmedAmount;
    }

    /** 一个账龄段的开放异常。 */
    @Getter
    @Setter
    public static class ExceptionAgeBucket {
        /** 账龄段：D0_3（0–3 天）、D4_7、D8_30、D31_PLUS（31 天及以上） */
        private String bucket;

        /** 开放异常数 */
        private Integer count;

        /** 其中阻塞对平的数量 */
        private Integer blockingCount;
    }

    /** 未匹配银行流水。 */
    @Getter
    @Setter
    public static class UnmatchedBankLines {
        /** 流水条数 */
        private Integer count;

        /** 贷方未匹配金额合计（金额减已匹配） */
        private String creditAmount;

        /** 借方未匹配金额合计（金额减已匹配） */
        private String debitAmount;
    }
}
