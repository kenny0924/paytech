package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;

import com.paytech.operations.console.bff.service.model.FinanceSetupModels.AccountingPeriodResponseBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 期间关账与重开页面模型（FIN-22）
 * <li>流程摘要与详情字段与 ledger PeriodCloseApi 合同一一对应，由 Client 适配器严格转换。</li>
 * <li>期间看板是 BFF 组合结果：日历下的会计期间各自附带最近一次关账/重开流程。</li>
 *
 * @author Claude
 */
public final class FinancePeriodCloseModels {

    /** 只承载类型，禁止创建实例。 */
    private FinancePeriodCloseModels() {
    }

    /** 关账/重开流程摘要。 */
    @Getter
    @Setter
    public static class PeriodCloseResponseBody {
        /** 流程主键。 */
        private String periodCloseId;

        /** 会计期间主键。 */
        private String periodId;

        /** 期间内流程序号。 */
        private String closeNo;

        /** 流程类型：CLOSE 或 REOPEN。 */
        private String closeType;

        /** 流程状态：PENDING_CHECK、COMPLETED、REJECTED、FAILED 等。 */
        private String status;

        /** 编制人 Account ID。 */
        private String makerId;

        /** 复核人 Account ID；未复核为空。 */
        private String checkerId;

        /** 关账摘要；PENDING_CHECK 起有值，FAILED 为空。 */
        private String closeHash;

        /** 首个失败步骤类型；未失败为空。 */
        private String failedStepType;

        /** 重开所引用原关账流程的期间内序号；仅 REOPEN。 */
        private String reopensCloseNo;

        /** 重开原因代码；仅 REOPEN。 */
        private String reopenReasonCode;

        /** 重开申请编号；仅 REOPEN。 */
        private String reopenRequestRef;

        /** 创建时间（UTC）。 */
        private Instant createTime;
    }

    /** 关账/重开流程详情：摘要、步骤、账簿证据与审批记录。 */
    @Getter
    @Setter
    public static class PeriodCloseDetailResponseBody extends PeriodCloseResponseBody {
        /** 按步骤号升序的步骤。 */
        private List<PeriodCloseStepResponseBody> steps;

        /** 按账簿主键升序的账簿证据。 */
        private List<PeriodCloseBookResponseBody> books;

        /** 审批记录。 */
        private List<PeriodCloseApprovalResponseBody> approvals;
    }

    /** 关账步骤。 */
    @Getter
    @Setter
    public static class PeriodCloseStepResponseBody {
        /** 步骤号，1—7。 */
        private Integer stepNo;

        /** 步骤类型。 */
        private String stepType;

        /** 步骤状态：PENDING、SUCCEEDED、FAILED 等。 */
        private String status;

        /** 结果摘要；未完成为空。 */
        private String resultHash;

        /** 完成时间（UTC）；未完成为空。 */
        private Instant completedTime;
    }

    /** 账簿证据。 */
    @Getter
    @Setter
    public static class PeriodCloseBookResponseBody {
        /** 账簿主键。 */
        private String ledgerBookId;

        /** 冻结的分录序号水位。 */
        private String asOfSequence;

        /** 最终试算主键；FINALIZED 前为空。 */
        private String finalTrialRunId;

        /** 损益结转指令主键；本期无收入费用时为空。 */
        private String postingInstructionId;

        /** 状态：LOCKED 或 FINALIZED。 */
        private String status;
    }

    /** 关账审批记录。 */
    @Getter
    @Setter
    public static class PeriodCloseApprovalResponseBody {
        /** 角色：MAKER 或 CHECKER。 */
        private String approvalRole;

        /** 审批人 Account ID。 */
        private String actorId;

        /** 决定：APPROVED 或 REJECTED。 */
        private String decision;

        /** 确认的关账摘要。 */
        private String approvedCloseHash;

        /** 决定时间（UTC）。 */
        private Instant decisionTime;
    }

    /** 发起期间重开；编制人取自会话。 */
    @Getter
    @Setter
    public static class StartPeriodReopenRequestBody {
        /** 重开原因代码：ACCOUNTING_CORRECTION、LATE_TRANSACTION 或 AUDIT_ADJUSTMENT，由 ledger 校验。 */
        @NotBlank(message = "重开原因必填")
        @Size(max = 64, message = "重开原因不超过 64 个字符")
        private String reasonCode;

        /** 批准本次重开的工单或操作申请稳定引用。 */
        @NotBlank(message = "申请编号必填")
        @Size(max = 128, message = "申请编号不超过 128 个字符")
        private String requestRef;
    }

    /** 批准或驳回关账/重开；复核人取自会话。 */
    @Getter
    @Setter
    public static class ReviewPeriodCloseRequestBody {
        /** 复核人确认的关账摘要；必须等于流程 closeHash。 */
        @NotBlank(message = "确认摘要必填")
        @Pattern(regexp = "[0-9a-f]{64}", message = "确认摘要必须是 64 位小写十六进制")
        private String approvedCloseHash;
    }

    /** 期间看板：日历下全部会计期间（财年、期号升序）及各自最近一次流程。 */
    @Getter
    @Setter
    public static class PeriodCloseBoardResponseBody {
        /** 财务日历主键。 */
        private String fiscalCalendarId;

        /** 看板行。 */
        private List<PeriodCloseBoardItemResponseBody> periods;
    }

    /** 看板行。 */
    @Getter
    @Setter
    public static class PeriodCloseBoardItemResponseBody {
        /** 会计期间。 */
        private AccountingPeriodResponseBody period;

        /** 最近一次关账/重开流程（序号最大）；从未发起时为空。 */
        private PeriodCloseResponseBody latestClose;
    }
}
