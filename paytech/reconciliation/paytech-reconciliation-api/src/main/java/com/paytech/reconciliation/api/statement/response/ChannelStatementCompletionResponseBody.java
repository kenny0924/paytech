package com.paytech.reconciliation.api.statement.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 渠道账单完成导入结果：通过为 MATCHING，不通过为 REJECTED 并带逐行原因。 */
@Getter
@Setter
public class ChannelStatementCompletionResponseBody {
    /** 渠道账单主键 */
    private String statementId;

    /** 完成后的状态：MATCHING 或 REJECTED */
    private String status;

    /** 已接收的明细行数 */
    private Integer importedLineCount;

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
         * <li>账单头或整单层面的原因（如合计不符、行数不符）为空。</li>
         */
        private Integer lineNo;

        /** 可外显的中文原因 */
        private String reason;
    }
}
