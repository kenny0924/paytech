package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.ApprovalDecision;
import com.paytech.finance.accounting.core.enums.ApprovalRole;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_adjustment_approval：追加保存人工会计调整固定双人审批的角色、决定和被批准内容摘要。 */
@Getter
@Setter
public class AdjustmentApproval {
    /**
     * 人工会计调整审批记录的服务侧雪花主键
     */
    private Long id;

    /**
     * 被审批的人工会计调整申请主键
     */
    private Long adjustmentId;

    /**
     * 本条审批记录承担的编制或复核角色
     * <li>允许值：MAKER、CHECKER。</li>
     */
    private ApprovalRole approvalRole;

    /**
     * 执行该审批动作的平台用户主键
     */
    private Long actorId;

    /**
     * 审批人对冻结调整内容作出的决定
     * <li>允许值：APPROVED、REJECTED。</li>
     */
    private ApprovalDecision decision;

    /**
     * 审批时确认且必须等于人工调整内容摘要的 SHA-256 小写十六进制值
     */
    private String approvedContentHash;

    /**
     * 审批决定形成的 UTC 微秒时间
     */
    private Instant decisionTime;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
