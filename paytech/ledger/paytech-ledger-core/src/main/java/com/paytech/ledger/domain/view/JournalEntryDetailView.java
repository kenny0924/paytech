package com.paytech.ledger.domain.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.JournalEntryType;

import lombok.Getter;
import lombok.Setter;

/** 分录头、来源指令与全部分录行（含账户科目与核算对象）的组合读取结果。 */
@Getter
@Setter
public class JournalEntryDetailView {
    /** 分录主键。 */
    private Long journalEntryId;

    /** 账簿主键。 */
    private Long ledgerBookId;

    /** 会计期间主键。 */
    private Long accountingPeriodId;

    /** 账簿内序号。 */
    private Long journalSequence;

    /** 过账日期。 */
    private LocalDate postingDate;

    /** 分录类型。 */
    private JournalEntryType entryType;

    /**
     * 被冲回的原分录主键
     * <li>非冲回分录为空。</li>
     */
    private Long reversalOfEntryId;

    /** 来源指令主键。 */
    private Long instructionId;

    /** 来源指令类型。 */
    private String instructionType;

    /** 指令来源。 */
    private InstructionOrigin originType;

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
    private Long manualAdjustmentId;

    /**
     * 人工调整单号
     * <li>自动指令为空。</li>
     */
    private String adjustmentNo;

    /** 按行号升序的分录行。 */
    private List<Line> lines;

    /** 分录行。 */
    @Getter
    @Setter
    public static class Line {
        /** 行号。 */
        private Integer lineNo;

        /** 账户主键。 */
        private Long accountId;

        /** 科目代码。 */
        private String accountCode;

        /** 核算对象类型。 */
        private OwnerType ownerType;

        /** 核算对象标识。 */
        private String ownerId;

        /** 余额桶。 */
        private BalanceBucket balanceBucket;

        /** 借贷方向。 */
        private EntrySide entrySide;

        /** 金额，按币种精度规范。 */
        private BigDecimal amount;
    }
}
