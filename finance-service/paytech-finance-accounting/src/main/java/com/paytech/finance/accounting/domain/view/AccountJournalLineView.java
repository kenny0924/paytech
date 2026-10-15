package com.paytech.finance.accounting.domain.view;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.core.enums.InstructionOrigin;
import com.paytech.finance.accounting.core.enums.JournalEntryType;

import lombok.Getter;
import lombok.Setter;

/** 账户的一条分录行及其分录头、来源与累计余额的组合读取结果。 */
@Getter
@Setter
public class AccountJournalLineView {
    /** 分录主键。 */
    private Long journalEntryId;

    /** 分录在账簿内的序号。 */
    private Long journalSequence;

    /** 过账日期。 */
    private LocalDate postingDate;

    /** 分录类型。 */
    private JournalEntryType entryType;

    /** 分录内行号。 */
    private Integer lineNo;

    /** 借贷方向。 */
    private EntrySide entrySide;

    /** 金额，按币种精度规范。 */
    private BigDecimal amount;

    /**
     * 该行之后的账户余额
     * <li>按账户正常方向对全量历史累计，不受日期过滤与分页影响。</li>
     */
    private BigDecimal balanceAfter;

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
     * 人工调整单号
     * <li>自动指令为空。</li>
     */
    private String adjustmentNo;
}
