package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按原版本启用或停用流程定义 / 动作模板。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeWorkflowStatusRequestBody {

    /**
     * 目标状态
     * <li>ACTIVE 或 INACTIVE。</li>
     */
    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE", message = "status 只能为 ACTIVE 或 INACTIVE")
    private String status;

    /** 读取时的版本；不符返回 409。 */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
