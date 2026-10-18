package com.paytech.ledger.service.processor.request;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.ledger.core.enums.EntrySide;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建并提交人工会计调整。 */
@Getter
@Setter
public class SubmitManualAdjustmentRequest {
    /** 账簿主键 */
    @NotNull
    private Long ledgerBookId;

    /** 会计期间主键 */
    @NotNull
    private Long accountingPeriodId;

    /** 调整币种，必须等于账簿币种 */
    @NotBlank
    private String currency;

    /** 调整原因代码 */
    @NotBlank
    private String reasonCode;

    /** Platform 证据文件主键，可为空 */
    private Long fileId;

    /** 证据文件内容摘要，与文件主键成对出现 */
    private String fileContentHash;

    /** 编制人 */
    @NotNull
    private Long makerId;

    /** 调整明细，至少两行 */
    @NotNull
    private List<@Valid Line> lines;

    /** 调整明细。 */
    @Getter
    @Setter
    public static class Line {
        /** 记账账户主键。 */
        @NotNull
        private Long accountId;

        /** 借贷方向。 */
        @NotNull
        private EntrySide entrySide;

        /** 金额，主货币单位。 */
        @NotNull
        private BigDecimal amount;

        /** 不含敏感值的业务说明。 */
        @NotBlank
        private String lineDescription;
    }
}
