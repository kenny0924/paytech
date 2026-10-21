package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道账单与平台流水页面模型
 * <li>字段与 reconciliation-api 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>操作人不在请求体中，由 BFF 从会话填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id。</li>
 *
 * @author Claude
 */
public final class ReconciliationStatementModels {

    /** 只承载类型，禁止创建实例。 */
    private ReconciliationStatementModels() {
    }

    /**
     * 开始导入渠道账单请求：标准账单 CSV 的账单头（#HEADER 行）加文件信息
     * <li>恒等式在完成导入时校验：grossAmount = Σ CREDIT 行 amount − Σ DEBIT 行 amount（不含 FEE 行）；feeAmount = Σ 明细 feeAmount +
     * Σ FEE 行 amount；netAmount 带 cashDirection 等于 grossAmount − feeAmount。</li>
     */
    @Getter
    @Setter
    public static class StartChannelStatementImportRequestBody {
        /**
         * 清算账户编码
         * <li>必须存在且 ACTIVE。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,63}", message = "必须是大写字母开头的大写字母、数字、下划线或连字符，2–64 位")
        private String clearingAccountCode;

        /**
         * 渠道结算批次号
         * <li>可见 ASCII，最长 128；同一清算账户 + 批次号只允许一个未作废版本。</li>
         */
        @NotBlank
        @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "必须是 1–128 位可见 ASCII 字符")
        private String channelBatchRef;

        /**
         * 渠道结算日
         * <li>YYYY-MM-DD。</li>
         */
        @NotNull
        private LocalDate settlementDate;

        /**
         * 账单币种
         * <li>必须等于清算账户币种。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
        private String currency;

        /**
         * 账单头总额
         * <li>带符号主货币单位十进制字符串：贷记减借记，不含 FEE 行。</li>
         */
        @NotBlank
        @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的带符号普通十进制字符串")
        private String grossAmount;

        /**
         * 账单头费用合计
         * <li>非负主货币单位十进制字符串。</li>
         */
        @NotBlank
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
        private String feeAmount;

        /**
         * 净额绝对值
         * <li>非负主货币单位十进制字符串，方向见 cashDirection；cashDirection 为 NONE 时必须为 0。</li>
         */
        @NotBlank
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
        private String netAmount;

        /**
         * 净额相对平台的方向
         * <li>CREDIT、DEBIT 或 NONE；切片 1 净额为借方（DEBIT）的账单在完成导入时拒绝。</li>
         */
        @NotBlank
        @Pattern(regexp = "CREDIT|DEBIT|NONE", message = "只能为 CREDIT、DEBIT 或 NONE")
        private String cashDirection;

        /**
         * 运营上传的原文件名
         * <li>仅用于展示，最长 255。</li>
         */
        @NotBlank
        @Size(max = 255)
        private String fileName;

        /**
         * 浏览器对整个原文件计算的 SHA-256
         * <li>64 位小写十六进制；服务不保存原文件。</li>
         */
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
        private String fileContentHash;

        /**
         * 声明的明细行数
         * <li>1–20,000；完成导入时必须等于已接收行数，行号 1..N 连续。</li>
         */
        @NotNull
        @Min(1)
        @Max(20000)
        private Integer declaredLineCount;
    }

    /** 追加一段渠道账单明细行请求：标准账单 CSV 的明细行，每段 1–100 行。 */
    @Getter
    @Setter
    public static class AppendChannelStatementLinesRequestBody {
        /**
         * 本段明细行
         * <li>1–100 行；同一段内行号不得重复。</li>
         */
        @NotNull
        @Size(min = 1, max = 100)
        private List<@Valid @NotNull Line> lines;

        /** 渠道账单明细行（CSV 一行）。 */
        @Getter
        @Setter
        public static class Line {
            /**
             * 文件内行号
             * <li>从 1 起，不超过声明行数；幂等键：同号同内容忽略，同号不同内容拒绝。</li>
             */
            @NotNull
            @Min(1)
            @Max(20000)
            private Integer lineNo;

            /**
             * 行类型
             * <li>PAYMENT、REFUND、CHARGEBACK、FEE 或 ADJUSTMENT。</li>
             */
            @NotBlank
            @Pattern(regexp = "PAYMENT|REFUND|CHARGEBACK|FEE|ADJUSTMENT", message = "只能为 PAYMENT、REFUND、CHARGEBACK、FEE 或 ADJUSTMENT")
            private String lineType;

            /**
             * 相对平台的资金方向
             * <li>CREDIT 或 DEBIT；ADJUSTMENT 行必须显式声明。</li>
             */
            @NotBlank
            @Pattern(regexp = "CREDIT|DEBIT", message = "只能为 CREDIT 或 DEBIT")
            private String direction;

            /**
             * 行金额
             * <li>非负主货币单位十进制字符串。</li>
             */
            @NotBlank
            @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
            private String amount;

            /**
             * 行上直接扣的渠道费
             * <li>非负主货币单位十进制字符串；没有费用填 0。</li>
             */
            @NotBlank
            @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
            private String feeAmount;

            /**
             * 外部交易号类型
             * <li>Payment 渠道交易引用类型目录中的 Code，非固定枚举；ADJUSTMENT 行与不带交易号的 FEE 行为空，其余行必填（完成导入时校验）；
             * 与 externalTransactionId 同有同无。</li>
             */
            @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 32 位")
            private String referenceType;

            /**
             * 渠道外部交易号
             * <li>可见 ASCII，最长 128；为空规则同 referenceType。</li>
             */
            @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "必须是 1–128 位可见 ASCII 字符")
            private String externalTransactionId;

            /**
             * 渠道记录的交易日期
             * <li>YYYY-MM-DD；可为空。</li>
             */
            private LocalDate transactionDate;

            /**
             * 标准列以外的渠道特有列
             * <li>列名到原值的映射，原样保存为 JSON Object，序列化后最大 4 KiB；不参与匹配。可为空。</li>
             */
            @Size(max = 64)
            private Map<@NotBlank @Size(max = 64) String, @NotNull @Size(max = 1024) String> rawAttributes;
        }
    }

    /** 渠道账单头与导入、对平进度。 */
    @Getter
    @Setter
    public static class ChannelStatementResponseBody {
        /** 渠道账单主键 */
        private String statementId;

        /** 清算账户主键 */
        private String clearingAccountId;

        /** 清算账户编码 */
        private String clearingAccountCode;

        /** 渠道结算批次号 */
        private String channelBatchRef;

        /** 同一批次号的导入版本，从 1 起 */
        private Integer revision;

        /** 渠道结算日 */
        private LocalDate settlementDate;

        /** 账单币种 */
        private String currency;

        /**
         * 账单头总额
         * <li>带符号：贷记减借记，不含 FEE 行；按币种精度补齐。</li>
         */
        private String grossAmount;

        /** 账单头费用合计，按币种精度补齐 */
        private String feeAmount;

        /** 净额绝对值，方向见 cashDirection，按币种精度补齐 */
        private String netAmount;

        /** 净额方向：CREDIT、DEBIT、NONE */
        private String cashDirection;

        /** 开始导入时声明的明细行数 */
        private Integer declaredLineCount;

        /** 已接收的明细行数 */
        private Integer importedLineCount;

        /** 原文件名 */
        private String fileName;

        /** 原文件 SHA-256 */
        private String fileContentHash;

        /**
         * 整单被拒的汇总原因
         * <li>只在 REJECTED 时非空。</li>
         */
        private String rejectReason;

        /** 状态：RECEIVING、REJECTED、MATCHING、VOIDED、RECONCILED、CASH_CONFIRMED */
        private String status;

        /** 净额中已经银行确认或归类结清的金额，按币种精度补齐 */
        private String cashConfirmedAmount;

        /**
         * 对平的 UTC 时间
         * <li>未对平为空。</li>
         */
        private Instant reconciledTime;

        /** 上传人 Account ID */
        private String makerId;

        /** 乐观锁版本 */
        private String version;

        /** 开始导入的 UTC 时间 */
        private Instant createTime;

        /** 最后更新的 UTC 时间 */
        private Instant updateTime;
    }

    /** 渠道账单完成导入结果：通过为 MATCHING，不通过为 REJECTED 并带逐行原因。 */
    @Getter
    @Setter
    public static class ChannelStatementCompletionResponseBody {
        /** 渠道账单主键 */
        private String statementId;

        /** 完成后的状态：MATCHING 或 REJECTED */
        private String status;

        /** 已接收的明细行数 */
        private Integer importedLineCount;

        /**
         * 整单被拒的汇总原因
         * <li>只在 REJECTED 时非空。</li>
         */
        private String rejectReason;

        /**
         * 逐行拒绝原因，按行号升序
         * <li>只在首次完成且 REJECTED 时非空；不落库，重复完成返回空列表。</li>
         */
        private List<LineRejection> rejections;

        /** 一条拒绝原因。 */
        @Getter
        @Setter
        public static class LineRejection {
            /**
             * 行号
             * <li>账单头或整单层面的原因（如合计不符、行数不符）为空。</li>
             */
            private Integer lineNo;

            /** 可外显的中文原因 */
            private String reason;
        }
    }

    /** 渠道账单详情：账单头、行计数、开放异常计数与对平门禁。 */
    @Getter
    @Setter
    public static class ChannelStatementDetailResponseBody {
        /** 账单头与进度 */
        private ChannelStatementResponseBody statement;

        /** 未匹配行数 */
        private Integer unmatchedLineCount;

        /** 已匹配（含已归类、已并入交易行的 FEE 行）行数 */
        private Integer matchedLineCount;

        /** 有未结异常的行数 */
        private Integer exceptionLineCount;

        /** 本账单开放异常（OPEN、UNDER_REVIEW）数 */
        private Integer openExceptionCount;

        /**
         * 本账单生成的预期银行流水主键
         * <li>未对平或净额方向为 NONE 时为空。</li>
         */
        private String expectedMovementId;

        /**
         * 对平门禁
         * <li>只对 MATCHING 账单计算；其他状态为空。</li>
         */
        private ReconcileGate gate;

        /** 账单对平门禁：全部为 true 才能对平。 */
        @Getter
        @Setter
        public static class ReconcileGate {
            /** 每条 PAYMENT / REFUND / CHARGEBACK 行都已匹配到平台流水 */
            private Boolean transactionLinesMatched;

            /** 每条 ADJUSTMENT 行和不带交易号的 FEE 行都已有 APPLIED 的归类处置 */
            private Boolean adjustmentLinesClassified;

            /** 本账单不存在阻塞性的开放异常 */
            private Boolean noBlockingException;

            /** 各行按方向回放后与账单头总额、费用、净额一致 */
            private Boolean totalsConsistent;

            /**
             * 清算账户已配置到账银行账户
             * <li>净额方向为 NONE 时恒为 true；为 false 时对平后无法生成预期银行流水，对平被拦下。</li>
             */
            private Boolean settlementCashAccountConfigured;
        }
    }

    /** 渠道账单行：文件内容与匹配、实际费用、归类结果。 */
    @Getter
    @Setter
    public static class ChannelStatementLineResponseBody {
        /** 账单行主键 */
        private String lineId;

        /** 渠道账单主键 */
        private String statementId;

        /** 文件内行号 */
        private Integer lineNo;

        /** 行类型：PAYMENT、REFUND、CHARGEBACK、FEE、ADJUSTMENT */
        private String lineType;

        /** 相对平台的方向：CREDIT、DEBIT */
        private String direction;

        /** 行金额，按币种精度补齐 */
        private String amount;

        /** 行上直接扣的渠道费，按币种精度补齐 */
        private String feeAmount;

        /** 外部交易号类型；ADJUSTMENT 行与不带交易号的 FEE 行为空 */
        private String referenceType;

        /** 渠道外部交易号；为空规则同 referenceType */
        private String externalTransactionId;

        /** 渠道记录的交易日期；可为空 */
        private LocalDate transactionDate;

        /** 标准列以外的渠道特有列；可为空 */
        private Map<String, String> rawAttributes;

        /**
         * 匹配到的平台流水主键
         * <li>未匹配为空。</li>
         */
        private String platformTxnId;

        /** 匹配方式：AUTO、MANUAL；未匹配为空 */
        private String matchMethod;

        /** 匹配的 UTC 时间；未匹配为空 */
        private Instant matchedTime;

        /**
         * 确认的实际渠道费（本行 feeAmount 加同交易号 FEE 行）
         * <li>FEE 行与未匹配行为空；按币种精度补齐。</li>
         */
        private String actualFee;

        /**
         * 已归类调整行指向的已应用处置主键
         * <li>未归类为空。</li>
         */
        private String resolutionId;

        /** 匹配状态：UNMATCHED、MATCHED、EXCEPTION */
        private String matchStatus;
    }

    /** 平台流水：由 billing 事实生成的逐笔渠道待清算应收或应付及其对账进度。 */
    @Getter
    @Setter
    public static class PlatformTxnResponseBody {
        /** 平台流水主键 */
        private String platformTxnId;

        /** 清算账户主键 */
        private String clearingAccountId;

        /** 方向：RECEIVABLE、PAYABLE */
        private String direction;

        /** 来源 billing 事实类型：PAYMENT、REFUND 等 */
        private String txnSource;

        /** 来源 billing 事实主键 */
        private String billingFactId;

        /** 经营主体编号 */
        private String entityNo;

        /** 来源业务对象类型：PAYMENT_ATTEMPT、CHANNEL_REFUND 等 */
        private String sourceRefType;

        /** 来源业务对象 ID */
        private String sourceRefId;

        /** Payment 渠道交易引用内部 ID */
        private String channelTransactionRefId;

        /** 渠道外部交易号类型 */
        private String referenceType;

        /** 渠道外部交易号 */
        private String externalTransactionId;

        /** 币种 */
        private String currency;

        /** 预期由渠道清算的金额，按币种精度补齐 */
        private String expectedAmount;

        /** billing 预估渠道费，按币种精度补齐 */
        private String expectedFee;

        /** 已被渠道账单行覆盖的金额，按币种精度补齐 */
        private String matchedAmount;

        /** 已匹配账单行确认的实际渠道费累计，按币种精度补齐 */
        private String actualFee;

        /** 已随账单对平登记或经归类结清的金额，按币种精度补齐 */
        private String settledAmount;

        /** 状态：OPEN、PARTIALLY_MATCHED、MATCHED、SETTLED、EXCEPTION */
        private String status;

        /** 来源事实业务发生的 UTC 时间 */
        private Instant businessTime;

        /** 乐观锁版本 */
        private String version;

        /** 登记的 UTC 时间 */
        private Instant createTime;

        /** 最后更新的 UTC 时间 */
        private Instant updateTime;
    }
}
