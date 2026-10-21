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
 * 对账异常、处置与在途汇总页面模型
 * <li>字段与 reconciliation-api 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>操作人不在请求体中，由 BFF 从会话填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id。</li>
 *
 * @author Claude
 */
public final class ReconciliationExceptionModels {

    /** 只承载类型，禁止创建实例。 */
    private ReconciliationExceptionModels() {
    }

    /** 人工开立对账异常请求：只指定来源对象，类型与差异金额由系统确定。 */
    @Getter
    @Setter
    public static class OpenReconExceptionRequestBody {
        /**
         * 来源对象类型
         * <li>只允许 PLATFORM_TXN、EXPECTED_BANK_MOVEMENT、BANK_STATEMENT_LINE。</li>
         */
        @NotBlank
        @Pattern(regexp = "PLATFORM_TXN|EXPECTED_BANK_MOVEMENT|BANK_STATEMENT_LINE",
            message = "只能为 PLATFORM_TXN、EXPECTED_BANK_MOVEMENT 或 BANK_STATEMENT_LINE")
        private String sourceType;

        /**
         * 来源对象主键
         */
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
        private String sourceId;
    }

    /**
     * 提交异常处置请求
     * <li>RE_MATCH：rematchTargetType + rematchTargetId 必填；银行层另须 rematchAmount；classificationCode、amount 必须为空。</li>
     * <li>ACCEPT_TIMING_DIFFERENCE：只填 note，其余为空。</li>
     * <li>CLASSIFY：classificationCode + amount 必填，amount 必须等于异常差异金额；重新匹配字段必须为空。</li>
     * <li>字段组合与可用范围在服务内按异常来源对象校验（见 {@code ReconClassification}）。</li>
     */
    @Getter
    @Setter
    public static class SubmitReconResolutionRequestBody {
        /**
         * 处置方式
         * <li>RE_MATCH、ACCEPT_TIMING_DIFFERENCE 或 CLASSIFY。</li>
         */
        @NotBlank
        @Pattern(regexp = "RE_MATCH|ACCEPT_TIMING_DIFFERENCE|CLASSIFY", message = "只能为 RE_MATCH、ACCEPT_TIMING_DIFFERENCE 或 CLASSIFY")
        private String resolutionType;

        /**
         * 归类码
         * <li>仅 CLASSIFY；必须在异常来源对象允许的范围内。</li>
         */
        @Pattern(regexp = "[A-Z][A-Z_]{0,31}", message = "必须是大写字母或下划线，最长 32 位")
        private String classificationCode;

        /**
         * 归类金额
         * <li>仅 CLASSIFY；带符号主货币单位十进制字符串，必须等于异常差异金额。</li>
         */
        @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的带符号普通十进制字符串")
        private String amount;

        /**
         * 重新匹配目标类型
         * <li>仅 RE_MATCH：交易层为 PLATFORM_TXN，银行层为 EXPECTED_BANK_MOVEMENT。</li>
         */
        @Pattern(regexp = "PLATFORM_TXN|EXPECTED_BANK_MOVEMENT", message = "只能为 PLATFORM_TXN 或 EXPECTED_BANK_MOVEMENT")
        private String rematchTargetType;

        /**
         * 重新匹配目标主键
         * <li>仅 RE_MATCH：交易层目标须同清算账户、方向一致、金额不超剩余；银行层须同银行账户。</li>
         */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
        private String rematchTargetId;

        /**
         * 银行层重新匹配的分配金额
         * <li>仅银行层 RE_MATCH；正数主货币单位十进制字符串，校验同人工多选匹配。</li>
         */
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
        private String rematchAmount;

        /**
         * 处置说明
         * <li>必填，最长 1000。</li>
         */
        @NotBlank
        @Size(max = 1000)
        private String note;
    }

    /** 复核异常处置请求；复核人取自 X-Gateway-Actor-Subject。 */
    @Getter
    @Setter
    public static class ReviewReconResolutionRequestBody {
        /**
         * 复核决定
         * <li>APPROVED：同一事务应用；REJECTED：异常回到 OPEN。</li>
         */
        @NotBlank
        @Pattern(regexp = "APPROVED|REJECTED", message = "只能为 APPROVED 或 REJECTED")
        private String decision;

        /**
         * 复核人确认的处置内容摘要
         * <li>必须等于提交时返回的 contentHash。</li>
         */
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
        private String approvedContentHash;
    }

    /** 对账异常及账龄。 */
    @Getter
    @Setter
    public static class ReconExceptionResponseBody {
        /** 异常主键 */
        private String exceptionId;

        /** 异常类型，见 ReconExceptionType */
        private String exceptionType;

        /** 对账层：TRANSACTION、BANK */
        private String reconLayer;

        /** 是否阻塞所属渠道账单对平 */
        private Boolean blocking;

        /** 来源对象类型，见 ReconExceptionSourceType */
        private String sourceType;

        /** 来源对象主键 */
        private String sourceId;

        /** 交易层异常所属的渠道账单主键；无关时为空 */
        private String statementId;

        /** 清算账户主键；银行层异常为空 */
        private String clearingAccountId;

        /** 银行账户主键；交易层异常为空 */
        private String cashAccountId;

        /** 主体类型：OPERATING_ENTITY、FINANCE_ENTITY */
        private String entityType;

        /** 主体标识 */
        private String entityId;

        /** 币种 */
        private String currency;

        /**
         * 系统算出的待处置差异
         * <li>带符号，按币种精度补齐；归类金额必须与之相等。</li>
         */
        private String differenceAmount;

        /** 系统生成的说明；可为空 */
        private String detail;

        /** 状态：OPEN、UNDER_REVIEW、RESOLVED、AUTO_CLOSED */
        private String status;

        /** 人工开立人 Account ID；系统开立为空 */
        private String openedBy;

        /** 开立的 UTC 时间 */
        private Instant openedTime;

        /**
         * 账龄天数
         * <li>未结为当前 UTC 时间与开立时间相差的整天数，已结为结案时间与开立时间相差的整天数。</li>
         */
        private Integer ageDays;

        /** 结案的 UTC 时间；未结为空 */
        private Instant closedTime;

        /** 乐观锁版本 */
        private String version;
    }

    /** 对账异常详情：异常、来源对象摘要、可用处置与处置历史。 */
    @Getter
    @Setter
    public static class ReconExceptionDetailResponseBody {
        /** 异常 */
        private ReconExceptionResponseBody exception;

        /** 来源对象摘要 */
        private SourceSummary source;

        /**
         * 当前可提交的处置方式
         * <li>异常不是 OPEN 时为空列表。</li>
         */
        private List<String> availableResolutionTypes;

        /**
         * 当前可选的归类码
         * <li>按来源对象限定；不允许 CLASSIFY 时为空列表。</li>
         */
        private List<String> availableClassificationCodes;

        /** 全部处置，按序号升序 */
        private List<ReconResolutionResponseBody> resolutions;

        /**
         * 所属账户的可读标识
         * <li>BFF 聚合：交易层异常补清算账户编码与渠道，银行层异常补银行账户名称与末四位；都不涉及时为空。</li>
         */
        private AccountLabel account;

        /** 异常所属清算账户或银行账户的可读标识（BFF 聚合）。 */
        @Getter
        @Setter
        public static class AccountLabel {
            /** 清算账户编码；银行层为空 */
            private String clearingAccountCode;

            /** 清算账户渠道编码；银行层为空 */
            private String channelCode;

            /** 银行账户显示名称；交易层为空 */
            private String cashAccountName;

            /** 银行账号末四位；交易层为空 */
            private String accountLast4;
        }

        /** 来源对象的关键字段，供页面识别。 */
        @Getter
        @Setter
        public static class SourceSummary {
            /** 来源对象类型 */
            private String sourceType;

            /** 来源对象主键 */
            private String sourceId;

            /**
             * 可读引用
             * <li>账单 / 账单行为渠道批次号与行号，平台流水为外部交易号，银行流水为银行交易号，预期银行流水为来源类型与来源 ID。</li>
             */
            private String reference;

            /** 方向（RECEIVABLE / PAYABLE 或 CREDIT / DEBIT）；账单与对账单为空 */
            private String direction;

            /** 对象金额，按币种精度补齐；对账单为空 */
            private String amount;

            /** 对象当前状态（账单状态、匹配状态等） */
            private String status;

            /** 业务日期：结算日、起息日、预期日或业务发生日；可为空 */
            private LocalDate businessDate;
        }
    }

    /** 异常处置与复核结果。 */
    @Getter
    @Setter
    public static class ReconResolutionResponseBody {
        /** 处置主键 */
        private String resolutionId;

        /** 异常主键 */
        private String exceptionId;

        /** 同一异常内从 1 起的处置序号 */
        private Integer resolutionNo;

        /** 处置方式：RE_MATCH、ACCEPT_TIMING_DIFFERENCE、CLASSIFY */
        private String resolutionType;

        /** 归类码；仅 CLASSIFY 非空 */
        private String classificationCode;

        /** 归类金额（带符号），按币种精度补齐；仅 CLASSIFY 非空 */
        private String amount;

        /** 重新匹配目标类型；仅 RE_MATCH 非空 */
        private String rematchTargetType;

        /** 重新匹配目标主键；仅 RE_MATCH 非空 */
        private String rematchTargetId;

        /** 银行层重新匹配的分配金额；仅银行层 RE_MATCH 非空 */
        private String rematchAmount;

        /** 处置说明 */
        private String note;

        /**
         * 处置内容摘要
         * <li>复核时须原样确认。</li>
         */
        private String contentHash;

        /** 提交人 Account ID */
        private String makerId;

        /** 复核人 Account ID；未复核为空 */
        private String checkerId;

        /** 复核决定：APPROVED、REJECTED；未复核为空 */
        private String checkerDecision;

        /** 复核的 UTC 时间；未复核为空 */
        private Instant decisionTime;

        /** 状态：PENDING_CHECK、REJECTED、APPLIED */
        private String status;

        /** 提交的 UTC 时间 */
        private Instant createTime;
    }

    /** 对账在途汇总：三块，金额按币种精度补齐。 */
    @Getter
    @Setter
    public static class ReconSummaryResponseBody {
        /** 经营主体编号；汇总全部时为空 */
        private String entityNo;

        /** 币种 */
        private String currency;

        /** 在途渠道账单：MATCHING（未对平）与 RECONCILED（已对平未到账） */
        private InTransitStatements inTransitStatements;

        /** 开放异常（OPEN、UNDER_REVIEW）按账龄段，段固定为 D0_3、D4_7、D8_30、D31_PLUS 且按此顺序 */
        private List<ExceptionAgeBucket> openExceptionsByAge;

        /** 未匹配银行流水（UNMATCHED、PARTIALLY_MATCHED、EXCEPTION） */
        private UnmatchedBankLines unmatchedBankLines;

        /** 在途渠道账单。 */
        @Getter
        @Setter
        public static class InTransitStatements {
            /** MATCHING 账单数 */
            private Integer matchingCount;

            /** MATCHING 账单净额合计（带符号：CREDIT 为正、DEBIT 为负） */
            private String matchingNetAmount;

            /** RECONCILED 账单数 */
            private Integer reconciledCount;

            /** RECONCILED 账单尚未银行确认的净额合计（带符号：净额减已确认金额） */
            private String reconciledUnconfirmedAmount;
        }

        /** 一个账龄段的开放异常。 */
        @Getter
        @Setter
        public static class ExceptionAgeBucket {
            /** 账龄段：D0_3（0–3 天）、D4_7、D8_30、D31_PLUS（31 天及以上） */
            private String bucket;

            /** 开放异常数 */
            private Integer count;

            /** 其中阻塞对平的数量 */
            private Integer blockingCount;
        }

        /** 未匹配银行流水。 */
        @Getter
        @Setter
        public static class UnmatchedBankLines {
            /** 流水条数 */
            private Integer count;

            /** 贷方未匹配金额合计（金额减已匹配） */
            private String creditAmount;

            /** 借方未匹配金额合计（金额减已匹配） */
            private String debitAmount;
        }
    }
}
