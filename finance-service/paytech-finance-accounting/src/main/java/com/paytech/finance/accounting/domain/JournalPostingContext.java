package com.paytech.finance.accounting.domain;

import java.time.LocalDate;
import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingInstruction;
import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;
import com.paytech.finance.accounting.core.enums.JournalEntryType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一条已验证指令在其账簿内过账所需的完整输入；过账服务不再回查指令来源。 */
@Getter
@AllArgsConstructor
public class JournalPostingContext {
    /** 状态为 VALIDATED、已持久化的记账指令。 */
    private final AccountingInstruction instruction;

    /** 指令的全部已持久化指令行。 */
    private final List<InstructionLine> lines;

    /** 分录归属的会计期间主键。 */
    private final long accountingPeriodId;

    /** 分录业务过账日期，必须落在期间区间内。 */
    private final LocalDate postingDate;

    /** 分录头类型。 */
    private final JournalEntryType entryType;
}
