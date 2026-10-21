package com.paytech.reconciliation.api.statement.response;

import lombok.Getter;
import lombok.Setter;

/** 渠道账单详情：账单头、行计数、开放异常计数与对平门禁。 */
@Getter
@Setter
public class ChannelStatementDetailResponseBody {
    /** 账单头与进度 */
    private ChannelStatementResponseBody statement;

    /** 未匹配行数 */
    private Integer unmatchedLineCount;

    /** 已匹配（含已归类、已并入交易行的 FEE 行）行数 */
    private Integer matchedLineCount;

    /** 有未结异常的行数 */
    private Integer exceptionLineCount;

    /** 本账单开放异常（OPEN、UNDER_REVIEW）数 */
    private Integer openExceptionCount;

    /**
     * 本账单生成的预期银行流水主键
     * <li>未对平或净额方向为 NONE 时为空。</li>
     */
    private String expectedMovementId;

    /**
     * 对平门禁
     * <li>只对 MATCHING 账单计算；其他状态为空。</li>
     */
    private ReconcileGate gate;

    /** 账单对平门禁：全部为 true 才能对平。 */
    @Getter
    @Setter
    public static class ReconcileGate {
        /** 每条 PAYMENT / REFUND / CHARGEBACK 行都已匹配到平台流水 */
        private Boolean transactionLinesMatched;

        /** 每条 ADJUSTMENT 行和不带交易号的 FEE 行都已有 APPLIED 的归类处置 */
        private Boolean adjustmentLinesClassified;

        /** 本账单不存在阻塞性的开放异常 */
        private Boolean noBlockingException;

        /** 各行按方向回放后与账单头总额、费用、净额一致 */
        private Boolean totalsConsistent;

        /**
         * 清算账户已配置到账银行账户
         * <li>净额方向为 NONE 时恒为 true；为 false 时对平后无法生成预期银行流水，对平被拦下。</li>
         */
        private Boolean settlementCashAccountConfigured;
    }
}
