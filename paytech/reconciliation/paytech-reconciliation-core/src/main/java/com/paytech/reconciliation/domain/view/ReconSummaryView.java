package com.paytech.reconciliation.domain.view;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 对账在途汇总（F5，#28）：渠道账单、对账异常与银行流水三张表的聚合结果
 * <li>三个嵌套类型是各自 Mapper 聚合 SQL 的结果行，只承载本次查询。</li>
 */
@Getter
@AllArgsConstructor
public class ReconSummaryView {
    /** 按状态汇总的在途渠道账单（MATCHING、RECONCILED） */
    private final List<StatementTotal> statementTotals;

    /** 按账龄段汇总的开放异常 */
    private final List<ExceptionAgeTotal> exceptionAgeTotals;

    /** 按方向汇总的未匹配银行流水 */
    private final List<UnmatchedLineTotal> unmatchedLineTotals;

    /** 一种状态的渠道账单合计。 */
    @Getter
    @Setter
    public static class StatementTotal {
        /** 账单状态 Code */
        private String status;

        /** 账单数 */
        private Integer statementCount;

        /** 净额合计（CREDIT 为正、DEBIT 为负） */
        private BigDecimal signedNetAmount;

        /** 到账确认金额合计 */
        private BigDecimal cashConfirmedAmount;
    }

    /** 一个账龄段的开放异常数。 */
    @Getter
    @Setter
    public static class ExceptionAgeTotal {
        /** 账龄段：D0_3、D4_7、D8_30、D31_PLUS */
        private String bucket;

        /** 异常数 */
        private Integer exceptionCount;

        /** 其中阻塞对平的数量 */
        private Integer blockingCount;
    }

    /** 一个方向的未匹配银行流水合计。 */
    @Getter
    @Setter
    public static class UnmatchedLineTotal {
        /** 方向 Code：CREDIT、DEBIT */
        private String direction;

        /** 流水条数 */
        private Integer lineCount;

        /** 未匹配金额合计（金额减已匹配） */
        private BigDecimal unmatchedAmount;
    }
}
