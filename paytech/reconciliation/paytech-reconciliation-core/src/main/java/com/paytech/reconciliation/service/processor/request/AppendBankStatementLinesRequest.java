package com.paytech.reconciliation.service.processor.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 追加一段银行流水行命令（每段 1–100 行，按行号幂等）。 */
@Getter
@AllArgsConstructor
public class AppendBankStatementLinesRequest {
    /** 上传人 Operations Account ID，须等于开始导入的上传人 */
    @NotNull
    private final Long actorId;

    /** 银行对账单主键 */
    @NotNull
    private final Long statementId;

    /** 本段流水行 */
    @NotNull
    @Size(min = 1, max = 100)
    private final List<@Valid @NotNull Line> lines;

    /** 一行银行流水（CSV 一行）。 */
    @Getter
    @Builder
    public static class Line {
        /** 行号 */
        @NotNull
        private final Integer lineNo;

        /** 银行交易号 */
        @NotBlank
        private final String bankTransactionRef;

        /** 起息日 */
        @NotNull
        private final LocalDate valueDate;

        /** 方向 */
        @NotNull
        private final SettlementDirection direction;

        /** 金额，正数 */
        @NotNull
        private final BigDecimal amount;

        /** 对方名称，可为空 */
        private final String counterpartyName;

        /** 附言或参考号，可为空 */
        private final String referenceText;
    }
}
