package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 校验动作模板配置（不保存）：返回与保存相同口径的语义问题列表。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ValidateWorkflowActionTemplateRequestBody {

    /** 动作类型<li>FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。</li> */
    @NotBlank
    @Pattern(regexp = "FORM|FILE_UPLOAD|PAGE_TASK|MANUAL_CONFIRM|DATA_QUERY", message = "actionType 不合法")
    private String actionType;

    /** 按类型的配置 JSON 对象；结构错误返回 422，字段路径以 config 为根。 */
    @NotNull
    private JsonNode config;
}
