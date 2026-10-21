package com.paytech.reconciliation.service.processor.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.SettlementDirection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 追加一段渠道账单明细行命令（每段 1–100 行，按行号幂等）。 */
@Getter
@AllArgsConstructor
public class AppendChannelStatementLinesRequest {
    /** 上传人 Operations Account ID，须等于开始导入的上传人 */
    @NotNull
    private final Long actorId;

    /** 渠道账单主键 */
    @NotNull
    private final Long statementId;

    /** 本段明细行 */
    @NotNull
    @Size(min = 1, max = 100)
    private final List<@Valid @NotNull Line> lines;

    /** 一行明细（CSV 一行）。 */
    @Getter
    @Builder
    public static class Line {
        /** 行号 */
        @NotNull
        private final Integer lineNo;

        /** 行类型 */
        @NotNull
        private final ChannelLineType lineType;

        /** 资金方向 */
        @NotNull
        private final SettlementDirection direction;

        /** 行金额，非负 */
        @NotNull
        private final BigDecimal amount;

        /** 行上费用，非负 */
        @NotNull
        private final BigDecimal feeAmount;

        /** 外部交易号类型；可为空 */
        private final String referenceType;

        /** 外部交易号；可为空 */
        private final String externalTransactionId;

        /** 交易日期；可为空 */
        private final LocalDate transactionDate;

        /** 标准列以外的渠道特有列；可为空 */
        private final Map<String, String> rawAttributes;
    }
}
