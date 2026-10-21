package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
 * 银行对账页面模型
 * <li>字段与 reconciliation-api 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>操作人不在请求体中，由 BFF 从会话填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id。</li>
 *
 * @author Claude
 */
public final class ReconciliationBankModels {

    /** 只承载类型，禁止创建实例。 */
    private ReconciliationBankModels() {
    }

    /**
     * 开始导入银行对账单请求：页面先选的银行账户、标准对账单 CSV 的头部与文件信息
     * <li>恒等式在完成导入时校验：openingBalance + Σ CREDIT 行 amount − Σ DEBIT 行 amount = closingBalance。</li>
     */
    @Getter
    @Setter
    public static class StartBankStatementImportRequestBody {
        /**
         * 银行账户主键
         * <li>页面先选；必须 ACTIVE。</li>
         */
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
        private String cashAccountId;

        /**
         * 文件头的银行账号末四位
         * <li>必须等于所选银行账户的末四位，否则拒绝。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9]{4}", message = "必须是 4 位字母或数字")
        private String bankAccountLast4;

        /**
         * 银行对账单编号
         * <li>可见 ASCII，最长 128；同一银行账户唯一。</li>
         */
        @NotBlank
        @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "必须是 1–128 位可见 ASCII 字符")
        private String statementRef;

        /**
         * 期间开始日（含）
         */
        @NotNull
        private LocalDate periodStart;

        /**
         * 期间结束日（含）
         * <li>不早于 periodStart。</li>
         */
        @NotNull
        private LocalDate periodEnd;

        /**
         * 币种
         * <li>必须等于银行账户币种。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
        private String currency;

        /**
         * 银行报告的期初余额
         * <li>带符号主货币单位十进制字符串。</li>
         */
        @NotBlank
        @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的带符号普通十进制字符串")
        private String openingBalance;

        /**
         * 银行报告的期末余额
         * <li>带符号主货币单位十进制字符串。</li>
         */
        @NotBlank
        @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的带符号普通十进制字符串")
        private String closingBalance;

        /**
         * 原文件名
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
         * 声明的流水行数
         * <li>0–20,000（期间无流水的对账单为 0，开始后直接完成）；完成导入时必须等于已接收行数，行号 1..N 连续。</li>
         */
        @NotNull
        @Min(0)
        @Max(20000)
        private Integer declaredLineCount;
    }

    /** 追加一段银行流水行请求：标准银行对账单 CSV 的明细行，每段 1–100 行。 */
    @Getter
    @Setter
    public static class AppendBankStatementLinesRequestBody {
        /**
         * 本段流水行
         * <li>1–100 行；同一段内行号不得重复。</li>
         */
        @NotNull
        @Size(min = 1, max = 100)
        private List<@Valid @NotNull Line> lines;

        /** 银行流水行（CSV 一行）。 */
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
             * 银行交易号
             * <li>可见 ASCII，最长 128；同一银行账户已入库的交易号在完成导入时跳过并计数。</li>
             */
            @NotBlank
            @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "必须是 1–128 位可见 ASCII 字符")
            private String bankTransactionRef;

            /**
             * 起息日
             * <li>YYYY-MM-DD，应在对账单期间内。</li>
             */
            @NotNull
            private LocalDate valueDate;

            /**
             * 相对平台账户的方向
             * <li>CREDIT 或 DEBIT。</li>
             */
            @NotBlank
            @Pattern(regexp = "CREDIT|DEBIT", message = "只能为 CREDIT 或 DEBIT")
            private String direction;

            /**
             * 流水金额
             * <li>正数主货币单位十进制字符串。</li>
             */
            @NotBlank
            @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
            private String amount;

            /**
             * 银行给出的对方名称
             * <li>可为空，最长 255。</li>
             */
            @Size(max = 255)
            private String counterpartyName;

            /**
             * 附言或参考号
             * <li>可为空，最长 500；仅用于人工识别。</li>
             */
            @Size(max = 500)
            private String referenceText;
        }
    }

    /**
     * 人工多选匹配请求：一条银行流水分配给多条预期银行流水
     * <li>每条分配对流水的带符号方向由预期流水方向与流水方向是否相同决定（同向为正）；带符号合计加已确认合计不超过流水金额。</li>
     */
    @Getter
    @Setter
    public static class CreateBankMatchesRequestBody {
        /**
         * 银行流水主键
         */
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
        private String bankStatementLineId;

        /**
         * 分配
         * <li>1–50 条；同一预期流水只能出现一次，必须与流水同一银行账户。</li>
         */
        @NotNull
        @Size(min = 1, max = 50)
        private List<@Valid @NotNull Allocation> allocations;

        /** 对一条预期银行流水的分配。 */
        @Getter
        @Setter
        public static class Allocation {
            /**
             * 预期银行流水主键
             */
            @NotBlank
            @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
            private String expectedMovementId;

            /**
             * 分配金额
             * <li>正数主货币单位十进制字符串，不超过预期流水剩余金额。</li>
             */
            @NotBlank
            @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
            private String amount;
        }
    }

    /** 银行对账单头与导入进度。 */
    @Getter
    @Setter
    public static class BankStatementResponseBody {
        /** 银行对账单主键 */
        private String bankStatementId;

        /** 银行账户主键 */
        private String cashAccountId;

        /** 银行对账单编号 */
        private String statementRef;

        /** 期间开始日（含） */
        private LocalDate periodStart;

        /** 期间结束日（含） */
        private LocalDate periodEnd;

        /** 币种 */
        private String currency;

        /** 银行报告的期初余额（带符号），按币种精度补齐 */
        private String openingBalance;

        /** 银行报告的期末余额（带符号），按币种精度补齐 */
        private String closingBalance;

        /** 开始导入时声明的流水行数 */
        private Integer declaredLineCount;

        /** 已接收的流水行数 */
        private Integer importedLineCount;

        /** 因银行交易号已存在而跳过的行数 */
        private Integer duplicateLineCount;

        /** 原文件名 */
        private String fileName;

        /** 原文件 SHA-256 */
        private String fileContentHash;

        /**
         * 整单被拒的汇总原因
         * <li>只在 REJECTED 时非空。</li>
         */
        private String rejectReason;

        /** 状态：RECEIVING、IMPORTED、REJECTED */
        private String status;

        /** 上传人 Account ID */
        private String makerId;

        /** 开始导入的 UTC 时间 */
        private Instant createTime;

        /** 最后更新的 UTC 时间 */
        private Instant updateTime;
    }

    /** 银行对账单完成导入结果：通过为 IMPORTED，不通过为 REJECTED 并带逐行原因。 */
    @Getter
    @Setter
    public static class BankStatementCompletionResponseBody {
        /** 银行对账单主键 */
        private String bankStatementId;

        /** 完成后的状态：IMPORTED 或 REJECTED */
        private String status;

        /** 已接收的流水行数 */
        private Integer importedLineCount;

        /** 因同一银行账户 + 银行交易号已存在而跳过的行数 */
        private Integer duplicateLineCount;

        /**
         * 与上一张对账单接不上时开立的 BANK_STATEMENT_GAP 异常主键
         * <li>接得上或 REJECTED 时为空。</li>
         */
        private String gapExceptionId;

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
             * <li>头部或整单层面的原因（如余额恒等式不成立、行数不符）为空。</li>
             */
            private Integer lineNo;

            /** 可外显的中文原因 */
            private String reason;
        }
    }

    /** 银行流水：文件内容与匹配进度。 */
    @Getter
    @Setter
    public static class BankStatementLineResponseBody {
        /** 银行流水主键 */
        private String bankStatementLineId;

        /** 银行对账单主键 */
        private String bankStatementId;

        /** 银行账户主键 */
        private String cashAccountId;

        /** 文件内行号 */
        private Integer lineNo;

        /** 银行交易号 */
        private String bankTransactionRef;

        /** 起息日 */
        private LocalDate valueDate;

        /** 方向：CREDIT、DEBIT */
        private String direction;

        /** 流水金额，按币种精度补齐 */
        private String amount;

        /** 币种（取自银行账户） */
        private String currency;

        /** 对方名称；可为空 */
        private String counterpartyName;

        /** 附言或参考号；可为空 */
        private String referenceText;

        /** 已确认匹配按方向带符号的合计（与本流水同向为正），按币种精度补齐 */
        private String matchedAmount;

        /** 匹配状态：UNMATCHED、PARTIALLY_MATCHED、MATCHED、EXCEPTION、SUSPENDED */
        private String matchStatus;

        /** 乐观锁版本 */
        private String version;
    }

    /** 预期银行流水与确认进度。 */
    @Getter
    @Setter
    public static class ExpectedBankMovementResponseBody {
        /** 预期银行流水主键 */
        private String expectedMovementId;

        /** 来源：CHANNEL_SETTLEMENT、PAYOUT、TAX_PAYMENT */
        private String movementType;

        /** 来源 ID：渠道账单主键 / 出款执行 ID / 缴税 ID */
        private String sourceId;

        /** 银行账户主键 */
        private String cashAccountId;

        /** 经营主体编号 */
        private String entityNo;

        /** 预期方向：CREDIT、DEBIT */
        private String direction;

        /** 币种 */
        private String currency;

        /** 预期金额，按币种精度补齐 */
        private String expectedAmount;

        /** 预期到账或扣款日 */
        private LocalDate expectedDate;

        /** 出款、缴税的银行付款参考号；渠道为空 */
        private String bankPaymentRef;

        /** 已确认银行匹配的金额合计，按币种精度补齐 */
        private String confirmedAmount;

        /** 经归类结清的差额合计（带符号：不足为正，超出为负），按币种精度补齐 */
        private String classifiedAmount;

        /** 状态：OPEN、PARTIALLY_CONFIRMED、CONFIRMED、CANCELLED、EXCEPTION */
        private String status;

        /** 乐观锁版本 */
        private String version;

        /** 生成的 UTC 时间 */
        private Instant createTime;

        /** 最后更新的 UTC 时间 */
        private Instant updateTime;
    }

    /** 银行匹配：一条银行流水对一条预期银行流水的分配。 */
    @Getter
    @Setter
    public static class BankMatchResponseBody {
        /** 银行匹配主键 */
        private String bankMatchId;

        /** 银行流水主键 */
        private String bankStatementLineId;

        /** 预期银行流水主键 */
        private String expectedMovementId;

        /**
         * 分配金额
         * <li>正数，按币种精度补齐；对流水的带符号方向由预期流水方向与流水方向是否相同决定。</li>
         */
        private String matchedAmount;

        /** 匹配方式：AUTO（系统建议）、MANUAL */
        private String matchMethod;

        /** 出款组成部分；仅 PAYOUT 使用（切片 3），其他为空 */
        private String payoutComponentType;

        /** 状态：PROPOSED、CONFIRMED、REJECTED */
        private String status;

        /** 由 RE_MATCH 处置建立时的处置主键；其他为空 */
        private String resolutionId;

        /** 确认或拒绝人 Account ID；建议未处理时为空 */
        private String operatorId;

        /** 确认或拒绝的 UTC 时间；建议未处理时为空 */
        private Instant decisionTime;

        /** 建立的 UTC 时间 */
        private Instant createTime;
    }

    /** 人工多选匹配建立的已确认匹配，顺序同请求分配。 */
    @Getter
    @Setter
    public static class BankMatchListResponseBody {
        /** 已确认的银行匹配 */
        private List<BankMatchResponseBody> items;
    }
}
