package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 账簿、账户、账户流水与分录详情页面模型
 * <li>字段与 ledger 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>金额为主货币单位字符串，已按账簿币种精度补齐；ID 与枚举均为字符串。</li>
 *
 * @author Claude
 */
public final class FinanceLedgerBookModels {
    /**
     * ledger 账户核算对象类型的校验正则
     * <li>与 ledger-api {@code LedgerOwnerType} 全部 Code 一致（注解需要编译期常量，由 auto 测试逐值核对）；Client 与 Local Partner 不在总账开户。</li>
     */
    public static final String LEDGER_OWNER_TYPE_PATTERN =
        "OPERATING_ENTITY|FINANCE_ENTITY|CHANNEL_ACCOUNT|CASH_ACCOUNT|TAX_AUTHORITY";

    /** 只承载类型，禁止创建实例。 */
    private FinanceLedgerBookModels() {
    }

    /** 账簿。 */
    @Getter
    @Setter
    public static class LedgerBookResponseBody {
        /** 账簿主键。 */
        private String ledgerBookId;

        /**
         * 主体类型
         * <li>OPERATING_ENTITY 或 FINANCE_ENTITY。</li>
         */
        private String entityType;

        /** 主体标识。 */
        private String entityId;

        /** 账簿币种。 */
        private String currency;

        /** 科目表代码。 */
        private String chartCode;

        /** 账簿状态。 */
        private String bookStatus;
    }

    /** 新建账簿；同主体同币种只能有一个账簿。 */
    @Getter
    @Setter
    public static class CreateLedgerBookRequestBody {
        /**
         * 主体类型
         * <li>OPERATING_ENTITY 或 FINANCE_ENTITY。</li>
         */
        @NotBlank(message = "主体类型必填")
        @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY", message = "主体类型只能为 OPERATING_ENTITY 或 FINANCE_ENTITY")
        private String entityType;

        /**
         * 主体标识
         * <li>1..64 个字符。</li>
         */
        @NotBlank(message = "主体标识必填")
        @Size(max = 64, message = "主体标识不超过 64 个字符")
        private String entityId;

        /**
         * 币种
         * <li>大写 ISO 4217 三位代码。</li>
         */
        @NotBlank(message = "币种必填")
        @Pattern(regexp = "[A-Z]{3}", message = "币种必须是大写 ISO 4217 三位代码")
        private String currency;

        /**
         * 科目表代码
         * <li>1..64 个字符。</li>
         */
        @NotBlank(message = "科目表代码必填")
        @Size(max = 64, message = "科目表代码不超过 64 个字符")
        private String chartCode;
    }

    /** 新建账簿结果。 */
    @Getter
    @Setter
    public static class CreateLedgerBookResponseBody {
        /** 新账簿主键。 */
        private String ledgerBookId;
    }

    /** 账簿内账户及余额。 */
    @Getter
    @Setter
    public static class LedgerAccountResponseBody {
        /** 账户主键。 */
        private String accountId;

        /** 可读账户号：{主体段}-{币种}-{科目编号}-{序号}，开户后不变。 */
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

        /** 正常余额方向。 */
        private String normalSide;

        /** 账户状态。 */
        private String accountStatus;

        /** 借方合计。 */
        private String debitTotal;

        /** 贷方合计。 */
        private String creditTotal;

        /**
         * 净余额
         * <li>按正常余额方向计算，反向余额为负数。</li>
         */
        private String netBalance;

        /** 最近一次过账的分录序号；未过账为空。 */
        private String lastJournalSequence;
    }

    /** 在账簿下新建账户。 */
    @Getter
    @Setter
    public static class CreateLedgerAccountRequestBody {
        /**
         * 科目代码
         * <li>1..64 个字符，必须可过账。</li>
         */
        @NotBlank(message = "科目代码必填")
        @Size(max = 64, message = "科目代码不超过 64 个字符")
        private String accountCode;

        /**
         * 核算对象类型
         * <li>OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、CASH_ACCOUNT 或 TAX_AUTHORITY；控制科目只能以账簿主体为核算对象。</li>
         */
        @NotBlank(message = "核算对象类型必填")
        @Pattern(regexp = LEDGER_OWNER_TYPE_PATTERN, message = "核算对象类型非法")
        private String ownerType;

        /**
         * 核算对象标识
         * <li>1..64 个字符。</li>
         */
        @NotBlank(message = "核算对象标识必填")
        @Size(max = 64, message = "核算对象标识不超过 64 个字符")
        private String ownerId;

        /**
         * 余额桶
         * <li>ledger 账户一律为 NONE；其他取值由 ledger 账户身份矩阵返回 ACCOUNT_IDENTITY_INVALID。</li>
         */
        @NotBlank(message = "余额桶必填")
        @Pattern(regexp = "PENDING|AVAILABLE|LOCKED|NONE", message = "余额桶非法")
        private String balanceBucket;
    }

    /** 新建账户结果。 */
    @Getter
    @Setter
    public static class CreateLedgerAccountResponseBody {
        /** 新账户主键。 */
        private String accountId;
    }

    /** 账户流水行。 */
    @Getter
    @Setter
    public static class AccountJournalLineResponseBody {
        /** 分录主键。 */
        private String journalEntryId;

        /** 账簿内分录序号。 */
        private String journalSequence;

        /** 过账日期。 */
        private LocalDate postingDate;

        /** 分录类型。 */
        private String entryType;

        /** 分录行号。 */
        private Integer lineNo;

        /** 借贷方向。 */
        private String entrySide;

        /** 金额。 */
        private String amount;

        /**
         * 行后余额
         * <li>按全量历史累计，筛选日期不改变起点。</li>
         */
        private String balanceAfter;

        /** 来源指令类型。 */
        private String instructionType;

        /** 来源类型：AUTO、MANUAL 或 ACCOUNT_POSTING（消费 account 过账批次生成）。 */
        private String originType;

        /** 自动来源系统；人工调整为空。 */
        private String sourceSystem;

        /** 自动来源事件键；人工调整为空。 */
        private String sourceEventKey;

        /** 人工调整单号；自动来源为空。 */
        private String adjustmentNo;

        /** account 过账批次主键；仅 ACCOUNT_POSTING 来源非空。 */
        private String accountPostingId;

        /** account 过账批次在其过账流内的序号；仅 ACCOUNT_POSTING 来源非空。 */
        private String accountPostingSequence;
    }

    /** 分录行。 */
    @Getter
    @Setter
    public static class JournalEntryLineResponseBody {
        /** 行号。 */
        private Integer lineNo;

        /** 账户主键。 */
        private String accountId;

        /** 账户的可读账户号。 */
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

        /** 借贷方向。 */
        private String entrySide;

        /** 金额。 */
        private String amount;

        /** 来源 account 明细流水主键；由 account 流水生成的控制科目行非空，规则行与人工行为空。 */
        private String accountEntryId;
    }

    /** 分录详情：分录头、来源与全部分录行。 */
    @Getter
    @Setter
    public static class JournalEntryDetailResponseBody {
        /** 分录主键。 */
        private String journalEntryId;

        /** 账簿主键。 */
        private String ledgerBookId;

        /** 会计期间主键。 */
        private String accountingPeriodId;

        /** 账簿内分录序号。 */
        private String journalSequence;

        /** 过账日期。 */
        private LocalDate postingDate;

        /** 分录类型。 */
        private String entryType;

        /** 被冲回的原分录主键；非冲回分录为空。 */
        private String reversalOfEntryId;

        /** 来源指令主键。 */
        private String instructionId;

        /** 来源指令类型。 */
        private String instructionType;

        /** 来源类型：AUTO、MANUAL 或 ACCOUNT_POSTING（消费 account 过账批次生成）。 */
        private String originType;

        /** 自动来源系统；人工调整为空。 */
        private String sourceSystem;

        /** 自动来源事件键；人工调整为空。 */
        private String sourceEventKey;

        /** 人工调整主键；自动来源为空。 */
        private String manualAdjustmentId;

        /** 人工调整单号；自动来源为空。 */
        private String adjustmentNo;

        /** account 过账批次主键；仅 ACCOUNT_POSTING 来源非空。 */
        private String accountPostingId;

        /** account 过账批次在其过账流内的序号；仅 ACCOUNT_POSTING 来源非空。 */
        private String accountPostingSequence;

        /** 全部分录行，按行号升序。 */
        private List<JournalEntryLineResponseBody> lines;
    }

    /** 科目余额行：末级科目或级次汇总科目。 */
    @Getter
    @Setter
    public static class AccountBalanceLineResponseBody {
        /** 科目主键。 */
        private String chartAccountId;

        /** 科目编号。 */
        private String accountNo;

        /** 科目代码。 */
        private String accountCode;

        /** 科目名称。 */
        private String accountName;

        /** 科目类别。 */
        private String accountClass;

        /** 正常余额方向；期初与期末余额按此方向表达。 */
        private String normalSide;

        /** 上级科目主键；一级科目为空。 */
        private String parentChartAccountId;

        /** 级次，1..4。 */
        private Integer level;

        /** 是否末级（可过账）；汇总行为 false。 */
        private Boolean postingAllowed;

        /** 期初余额。 */
        private String openingBalance;

        /** 本期借方发生额。 */
        private String periodDebit;

        /** 本期贷方发生额。 */
        private String periodCredit;

        /** 期末余额。 */
        private String closingBalance;
    }

    /** 账簿某期间的实时科目余额。 */
    @Getter
    @Setter
    public static class AccountBalanceListResponseBody {
        /** 账簿主键，与请求一致。 */
        private String ledgerBookId;

        /** 会计期间主键，与请求一致。 */
        private String periodId;

        /** 账簿币种。 */
        private String currency;

        /** 有发生额或余额的末级科目及其各级上级，按科目编号排序。 */
        private List<AccountBalanceLineResponseBody> lines;
    }

    /** 试算摘要。 */
    @Getter
    @Setter
    public static class TrialBalanceRunResponseBody {
        /** 试算主键。 */
        private String trialBalanceRunId;

        /** 账簿主键。 */
        private String ledgerBookId;

        /** 会计期间主键。 */
        private String periodId;

        /** 账簿期间内的试算序号。 */
        private String runNo;

        /** 试算时的分录序号水位。 */
        private String asOfSequence;

        /** 试算状态。 */
        private String status;

        /** 借方合计。 */
        private String totalDebit;

        /** 贷方合计。 */
        private String totalCredit;

        /** 试算内容摘要。 */
        private String contentHash;

        /** 末级试算行数。 */
        private Integer lineCount;

        /** 异常数。 */
        private Integer exceptionCount;

        /** 试算时间，UTC。 */
        private Instant createTime;
    }

    /** 试算异常。 */
    @Getter
    @Setter
    public static class TrialBalanceFindingResponseBody {
        /** 异常类型。 */
        private String exceptionType;

        /** 关联对象类型。 */
        private String referenceType;

        /** 关联对象主键。 */
        private String referenceId;

        /** 差额。 */
        private String differenceAmount;

        /** 异常状态。 */
        private String status;
    }

    /** 试算详情：摘要、末级试算行、级次汇总行与异常。 */
    @Getter
    @Setter
    public static class TrialBalanceDetailResponseBody extends TrialBalanceRunResponseBody {
        /** 末级试算行，按科目编号排序。 */
        private List<AccountBalanceLineResponseBody> lines;

        /** 查询时汇总的级次汇总行，postingAllowed 为 false。 */
        private List<AccountBalanceLineResponseBody> summaryLines;

        /** 异常。 */
        private List<TrialBalanceFindingResponseBody> exceptions;
    }
}
