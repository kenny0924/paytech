package com.paytech.ledger.domain;

import java.time.LocalDate;
import java.util.List;

import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.AdjustmentApproval;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.enums.JournalEntryType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** 审批人工调整并在同一事务内过账：调整、Checker 记录、人工指令及其过账结果。 */
@Getter
@RequiredArgsConstructor
public class ApproveManualAdjustmentContext {
    /** 当前为 SUBMITTED 的调整头。 */
    private final ManualAdjustment adjustment;

    /** Checker 批准记录。 */
    private final AdjustmentApproval checkerApproval;

    /** 由调整生成、状态为 VALIDATED 的人工指令。 */
    private final AccountingInstruction instruction;

    /** 与调整明细一一对应的指令行。 */
    private final List<InstructionLine> instructionLines;

    /** 按日历时区确定的过账日期。 */
    private final LocalDate postingDate;

    /** 过账生成的分录头；过账前为空。 */
    @Setter
    private JournalEntry journalEntry;

    /** 组装过账服务的输入。 */
    public JournalPostingContext postingContext() {
        return new JournalPostingContext(instruction, instructionLines, adjustment.getAccountingPeriodId(), postingDate,
            JournalEntryType.ADJUSTMENT);
    }
}
