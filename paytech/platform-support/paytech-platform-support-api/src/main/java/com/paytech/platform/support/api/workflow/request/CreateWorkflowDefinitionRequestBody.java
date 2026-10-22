package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 新建流程定义及草稿 v1（只有开始与结束节点）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkflowDefinitionRequestBody {

    /**
     * 流程编码
     * <li>大写字母开头，大写字母、数字或下划线，最长 64；创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "definitionCode 格式不合法")
    private String definitionCode;

    /**
     * 流程名称
     * <li>最长 128。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String definitionName;

    /**
     * 流程说明
     * <li>可空，最长 512。</li>
     */
    @Size(max = 512)
    private String description;
}
