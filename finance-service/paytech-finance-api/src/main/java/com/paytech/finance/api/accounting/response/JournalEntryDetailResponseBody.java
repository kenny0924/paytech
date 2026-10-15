package com.paytech.finance.api.accounting.response;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 分录详情：分录头、来源与全部分录行。 */
@Getter
@Setter
public class JournalEntryDetailResponseBody {
    /** 分录主键。 */
    private String journalEntryId;

    /** 账簿主键。 */
    private String ledgerBookId;

    /** 会计期间主键。 */
    private String accountingPeriodId;

    /** 账簿内序号。 */
    private String journalSequence;

    /** 过账日期。 */
    private LocalDate postingDate;

    /** 分录类型。 */
    private String entryType;

    /**
     * 被冲回的原分录主键
     * <li>非冲回分录为空。</li>
     */
    private String reversalOfEntryId;

    /** 来源指令主键。 */
    private String instructionId;

    /** 来源指令类型。 */
    private String instructionType;

    /** 指令来源：AUTO 或 MANUAL。 */
    private String originType;

    /**
     * 自动指令来源系统
     * <li>人工指令为空。</li>
     */
    private String sourceSystem;

    /**
     * 自动指令来源事件键
     * <li>人工指令为空。</li>
     */
    private String sourceEventKey;

    /**
     * 人工调整主键
     * <li>自动指令为空。</li>
     */
    private String manualAdjustmentId;

    /**
     * 人工调整单号
     * <li>自动指令为空。</li>
     */
    private String adjustmentNo;

    /** 按行号升序的全部分录行。 */
    private List<Line> lines;

    /** 分录行。 */
    @Getter
    @Setter
    public static class Line {
        /** 行号。 */
        private Integer lineNo;

        /** 账户主键。 */
        private String accountId;

        /** 科目代码。 */
        private String accountCode;

        /** 核算对象类型。 */
        private String ownerType;

        /** 核算对象标识。 */
        private String ownerId;

        /** 余额桶。 */
        private String balanceBucket;

        /** 借贷方向：DEBIT 或 CREDIT。 */
        private String entrySide;

        /** 金额，主货币单位，按账簿币种精度补齐。 */
        private String amount;
    }
}
