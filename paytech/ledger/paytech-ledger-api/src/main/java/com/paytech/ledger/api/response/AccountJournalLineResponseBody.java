package com.paytech.ledger.api.response;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 账户流水行。 */
@Getter
@Setter
public class AccountJournalLineResponseBody {
    /** 分录主键。 */
    private String journalEntryId;

    /** 分录在账簿内的序号。 */
    private String journalSequence;

    /** 过账日期。 */
    private LocalDate postingDate;

    /**
     * 分录类型
     * <li>STANDARD、REVERSAL、ADJUSTMENT、CLOSING 或 REOPENING。</li>
     */
    private String entryType;

    /** 分录内行号。 */
    private Integer lineNo;

    /**
     * 借贷方向
     * <li>DEBIT 或 CREDIT。</li>
     */
    private String entrySide;

    /** 金额，主货币单位，按账簿币种精度补齐。 */
    private String amount;

    /**
     * 该行之后的账户余额
     * <li>按账户正常方向对全量历史累计，日期过滤与分页不改变累计起点；负数表示反向余额。</li>
     */
    private String balanceAfter;

    /** 来源指令类型，例如 MANUAL_ADJUSTMENT。 */
    private String instructionType;

    /**
     * 指令来源
     * <li>AUTO 或 MANUAL。</li>
     */
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
     * 人工调整单号
     * <li>自动指令为空。</li>
     */
    private String adjustmentNo;
}
