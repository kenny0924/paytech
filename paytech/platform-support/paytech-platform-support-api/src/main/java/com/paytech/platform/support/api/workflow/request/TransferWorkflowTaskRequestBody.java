package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 转交动作或审批。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferWorkflowTaskRequestBody {

    /**
     * 新处理人账号 ID
     * <li>必须是 ACTIVE 的 Operations 账号，不能是自己或当前处理人。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "ID 必须是十进制字符串")
    private String toUserId;

    /**
     * 转交说明
     * <li>可空，最长 512。</li>
     */
    @Size(max = 512)
    private String reason;
}
