package com.paytech.ledger.core.dal.dataobject;

import java.time.Instant;

import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;

import lombok.Getter;
import lombok.Setter;

/** t_close_approval：追加记录关账或重开流程固定 Maker-Checker 审批及实际批准的关账内容哈希。 */
@Getter
@Setter
public class CloseApproval {
    /**
     * 关账或重开审批记录的 Snowflake 主键
     */
    private Long id;

    /**
     * 被审批的本库关账或重开流程
     */
    private Long periodCloseId;

    /**
     * 固定双人控制中的提交或复核角色
     * <li>允许值：MAKER、CHECKER。</li>
     */
    private ApprovalRole approvalRole;

    /**
     * Platform 身份系统中的审批操作人标识
     */
    private Long actorId;

    /**
     * 对关账或重开流程作出的批准或拒绝决定
     * <li>允许值：APPROVED、REJECTED。</li>
     */
    private ApprovalDecision decision;

    /**
     * 审批人实际审阅且必须等于流程关账内容哈希的值
     */
    private String approvedCloseHash;

    /**
     * 审批决定发生的 UTC 微秒时间
     */
    private Instant decisionTime;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
