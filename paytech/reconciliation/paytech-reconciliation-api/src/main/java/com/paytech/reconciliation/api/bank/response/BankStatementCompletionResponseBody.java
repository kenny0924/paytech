package com.paytech.reconciliation.api.bank.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 银行对账单完成导入结果：通过为 IMPORTED，不通过为 REJECTED 并带逐行原因。 */
@Getter
@Setter
public class BankStatementCompletionResponseBody {
    /** 银行对账单主键 */
    private String bankStatementId;

    /** 完成后的状态：IMPORTED 或 REJECTED */
    private String status;

    /** 已接收的流水行数 */
    private Integer importedLineCount;

    /** 因同一银行账户 + 银行交易号已存在而跳过的行数 */
    private Integer duplicateLineCount;

    /**
     * 与上一张对账单接不上时开立的 BANK_STATEMENT_GAP 异常主键
     * <li>接得上或 REJECTED 时为空。</li>
     */
    private String gapExceptionId;

    /**
     * 整单被拒的汇总原因
     * <li>只在 REJECTED 时非空。</li>
     */
    private String rejectReason;

    /**
     * 逐行拒绝原因，按行号升序
     * <li>只在首次完成且 REJECTED 时非空；不落库，重复完成返回空列表。</li>
     */
    private List<LineRejection> rejections;

    /** 一条拒绝原因。 */
    @Getter
    @Setter
    public static class LineRejection {
        /**
         * 行号
         * <li>头部或整单层面的原因（如余额恒等式不成立、行数不符）为空。</li>
         */
        private Integer lineNo;

        /** 可外显的中文原因 */
        private String reason;
    }
}
