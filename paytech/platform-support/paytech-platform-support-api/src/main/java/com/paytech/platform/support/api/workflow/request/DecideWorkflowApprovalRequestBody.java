package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 审批决定。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DecideWorkflowApprovalRequestBody {

    /**
     * 决定
     * <li>APPROVE 同意、RETURN 退回修改、REJECT 驳回。</li>
     */
    @NotBlank
    @Pattern(regexp = "APPROVE|RETURN|REJECT", message = "decision 不合法")
    private String decision;

    /**
     * 意见
     * <li>退回修改与驳回必填，最长 512。</li>
     */
    @Size(max = 512)
    private String comment;

    /**
     * 驳回目标
     * <li>汇合后的第一个节点驳回时必填，取详情中给出的分支末任务节点。</li>
     */
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "key 格式不合法")
    private String rejectTargetNodeKey;
}
