package com.paytech.ledger.domain.view;

import com.paytech.billing.api.enums.AutoInstructionType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 交易自动记账生成（或幂等返回）的一条指令及其分录。 */
@Getter
@AllArgsConstructor
public class PostedInstructionView {
    /** 记账指令主键。 */
    private final Long instructionId;

    /** 指令所属账簿主键。 */
    private final Long ledgerBookId;

    /** 指令类型。 */
    private final AutoInstructionType instructionType;

    /** 过账生成的分录主键。 */
    private final Long journalEntryId;

    /** 分录在账簿内的序号。 */
    private final Long journalSequence;
}
