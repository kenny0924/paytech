package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 新建动作模板。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkflowActionTemplateRequestBody {

    /**
     * 模板编码
     * <li>大写字母开头，大写字母、数字或下划线，最长 64；创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "templateCode 格式不合法")
    private String templateCode;

    /**
     * 模板名称
     * <li>最长 128。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String templateName;

    /**
     * 动作类型
     * <li>FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY；DATA_QUERY 只预留，不能被发布引用。</li>
     */
    @NotBlank
    @Pattern(regexp = "FORM|FILE_UPLOAD|PAGE_TASK|MANUAL_CONFIRM|DATA_QUERY", message = "actionType 不合法")
    private String actionType;

    /**
     * 模板说明
     * <li>可空，最长 512。</li>
     */
    @Size(max = 512)
    private String description;

    /**
     * 按类型的配置对象
     * <li>结构见技术方案 3.3。</li>
     */
    @NotNull
    private JsonNode config;
}
