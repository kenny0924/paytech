package com.paytech.ledger.api.response;

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

    /**
     * 指令来源
     * <li>AUTO、MANUAL 或 ACCOUNT_POSTING（消费 account 过账批次生成）。</li>
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
     * 人工调整主键
     * <li>自动指令为空。</li>
     */
    private String manualAdjustmentId;

    /**
     * 人工调整单号
     * <li>自动指令为空。</li>
     */
    private String adjustmentNo;

    /**
     * account 过账批次主键，十进制字符串
     * <li>仅 originType = ACCOUNT_POSTING 时非空。</li>
     */
    private String accountPostingId;

    /**
     * account 过账批次在其过账流内的序号，十进制字符串
     * <li>仅 originType = ACCOUNT_POSTING 时非空。</li>
     */
    private String accountPostingSequence;

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

        /** 财务人员可读账户号。 */
        private String accountNo;

        /** 科目代码。 */
        private String accountCode;

        /** 科目名称。 */
        private String accountName;

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

        /**
         * 来源 account 明细流水主键，十进制字符串
         * <li>由 account 流水生成的控制科目行非空；规则行、人工行为空。</li>
         */
        private String accountEntryId;
    }
}
