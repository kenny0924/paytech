package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 人工会计调整页面模型（FIN-10）
 * <li>字段与 ledger ManualAdjustmentApi 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>编制人与复核人不在请求体中，由 BFF 从会话填入 X-Gateway-Actor-Subject。</li>
 *
 * @author Claude
 */
public final class FinanceManualAdjustmentModels {

    /** 只承载类型，禁止创建实例。 */
    private FinanceManualAdjustmentModels() {
    }

    /** 人工调整列表元素。 */
    @Getter
    @Setter
    public static class ManualAdjustmentResponseBody {
        /** 调整主键。 */
        private String manualAdjustmentId;

        /** 调整业务单号。 */
        private String adjustmentNo;

        /** 账簿主键。 */
        private String ledgerBookId;

        /** 会计期间主键。 */
        private String accountingPeriodId;

        /** 调整币种。 */
        private String currency;

        /** 调整原因代码。 */
        private String reasonCode;

        /** 调整状态：SUBMITTED、POSTED 或 REJECTED。 */
        private String status;

        /** 借方合计，按币种精度补齐。 */
        private String totalDebit;

        /** 贷方合计，按币种精度补齐。 */
        private String totalCredit;

        /** 编制人 Account ID。 */
        private String makerId;

        /** 提交时间（UTC）。 */
        private Instant submittedTime;

        /** 过账分录主键；未过账为空。 */
        private String journalEntryId;
    }

    /** 人工调整详情：调整头、明细、审批记录与过账结果。 */
    @Getter
    @Setter
    public static class ManualAdjustmentDetailResponseBody {
        /** 调整主键。 */
        private String manualAdjustmentId;

        /** 调整业务单号。 */
        private String adjustmentNo;

        /** 账簿主键。 */
        private String ledgerBookId;

        /** 会计期间主键。 */
        private String accountingPeriodId;

        /** 调整币种。 */
        private String currency;

        /** 调整原因代码。 */
        private String reasonCode;

        /** 证据文件主键；可为空。 */
        private String fileId;

        /** 证据文件内容摘要；可为空。 */
        private String fileContentHash;

        /** 借方合计，按币种精度补齐。 */
        private String totalDebit;

        /** 贷方合计，按币种精度补齐。 */
        private String totalCredit;

        /** 调整内容摘要；复核时原样确认。 */
        private String contentHash;

        /** 调整状态。 */
        private String status;

        /** 编制人 Account ID。 */
        private String makerId;

        /** 提交时间（UTC）。 */
        private Instant submittedTime;

        /** 按行号升序的明细。 */
        private List<ManualAdjustmentLineResponseBody> lines;

        /** 审批记录。 */
        private List<ManualAdjustmentApprovalResponseBody> approvals;

        /** 过账分录主键；未过账为空。 */
        private String journalEntryId;

        /** 分录账簿序号；未过账为空。 */
        private String journalSequence;

        /** 过账日期；未过账为空。 */
        private LocalDate postingDate;
    }

    /** 人工调整明细。 */
    @Getter
    @Setter
    public static class ManualAdjustmentLineResponseBody {
        /** 行号。 */
        private Integer lineNo;

        /** 记账账户主键。 */
        private String accountId;

        /** 借贷方向：DEBIT 或 CREDIT。 */
        private String entrySide;

        /** 金额，按币种精度补齐。 */
        private String amount;

        /** 业务说明。 */
        private String lineDescription;
    }

    /** 人工调整审批记录。 */
    @Getter
    @Setter
    public static class ManualAdjustmentApprovalResponseBody {
        /** 审批角色：MAKER 或 CHECKER。 */
        private String approvalRole;

        /** 审批人 Account ID。 */
        private String actorId;

        /** 决定：APPROVED 或 REJECTED。 */
        private String decision;

        /** 审批确认的内容摘要。 */
        private String approvedContentHash;

        /** 决定时间（UTC）。 */
        private Instant decisionTime;
    }

    /** 创建并提交人工调整；编制人取自会话。 */
    @Getter
    @Setter
    public static class SubmitManualAdjustmentRequestBody {
        /** 调整账簿主键。 */
        @NotBlank(message = "账簿必填")
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "账簿主键必须是正十进制整数字符串")
        private String ledgerBookId;

        /** 调整会计期间主键；必须 OPEN 且属于账簿主体。 */
        @NotBlank(message = "会计期间必填")
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "会计期间主键必须是正十进制整数字符串")
        private String accountingPeriodId;

        /** 调整币种；必须等于账簿币种。 */
        @NotBlank(message = "币种必填")
        @Pattern(regexp = "[A-Z]{3}", message = "币种必须是大写 ISO 4217 三位代码")
        private String currency;

        /** 调整原因代码。 */
        @NotBlank(message = "调整原因必填")
        @Size(max = 64, message = "调整原因不超过 64 个字符")
        private String reasonCode;

        /** Platform 证据文件主键；可为空，与 fileContentHash 成对提供。 */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "证据文件主键必须是正十进制整数字符串")
        private String fileId;

        /** 证据文件内容摘要；64 位小写十六进制，与 fileId 成对提供。 */
        @Pattern(regexp = "[0-9a-f]{64}", message = "证据文件摘要必须是 64 位小写十六进制")
        private String fileContentHash;

        /** 调整明细；至少两行，借贷合计相等。 */
        @NotNull(message = "调整明细必填")
        @Size(min = 2, max = 500, message = "调整明细为 2 至 500 行")
        private List<@Valid @NotNull SubmitManualAdjustmentLineRequestBody> lines;
    }

    /** 人工调整明细行。 */
    @Getter
    @Setter
    public static class SubmitManualAdjustmentLineRequestBody {
        /** 记账账户主键；必须属于调整账簿且为 ACTIVE。 */
        @NotBlank(message = "记账账户必填")
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "记账账户主键必须是正十进制整数字符串")
        private String accountId;

        /** 借贷方向：DEBIT 或 CREDIT。 */
        @NotBlank(message = "借贷方向必填")
        @Pattern(regexp = "DEBIT|CREDIT", message = "借贷方向只能为 DEBIT 或 CREDIT")
        private String entrySide;

        /** 金额：主货币单位十进制字符串，大于零且不超过币种精度。 */
        @NotBlank(message = "金额必填")
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "金额必须是主货币单位的普通十进制字符串")
        private String amount;

        /** 不含敏感值的业务说明。 */
        @NotBlank(message = "业务说明必填")
        @Size(max = 512, message = "业务说明不超过 512 个字符")
        private String lineDescription;
    }

    /** 提交人工调整结果。 */
    @Getter
    @Setter
    public static class SubmitManualAdjustmentResponseBody {
        /** 调整主键。 */
        private String manualAdjustmentId;

        /** 调整业务单号。 */
        private String adjustmentNo;

        /** 调整内容摘要；复核时须原样确认。 */
        private String contentHash;

        /** 调整状态；提交成功为 SUBMITTED。 */
        private String status;
    }

    /** 批准或驳回人工调整；复核人取自会话。 */
    @Getter
    @Setter
    public static class ReviewManualAdjustmentRequestBody {
        /** 复核人确认的调整内容摘要；必须等于详情中的 contentHash。 */
        @NotBlank(message = "确认摘要必填")
        @Pattern(regexp = "[0-9a-f]{64}", message = "确认摘要必须是 64 位小写十六进制")
        private String approvedContentHash;
    }

    /** 复核人工调整结果。 */
    @Getter
    @Setter
    public static class ReviewManualAdjustmentResponseBody {
        /** 调整主键。 */
        private String manualAdjustmentId;

        /** 调整状态：批准后为 POSTED，驳回后为 REJECTED。 */
        private String status;

        /** 过账分录主键；驳回时为空。 */
        private String journalEntryId;

        /** 分录账簿序号；驳回时为空。 */
        private String journalSequence;

        /** 过账日期；驳回时为空。 */
        private LocalDate postingDate;
    }
}
