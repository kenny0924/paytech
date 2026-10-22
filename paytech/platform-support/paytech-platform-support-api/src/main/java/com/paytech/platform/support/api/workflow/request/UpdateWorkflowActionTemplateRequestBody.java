package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 按原版本修改动作模板；类型与编码不可改，已发布版本使用冻结快照不受影响。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkflowActionTemplateRequestBody {

    /**
     * 模板名称
     * <li>最长 128。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String templateName;

    /**
     * 模板说明
     * <li>可空，最长 512。</li>
     */
    @Size(max = 512)
    private String description;

    /** 按类型的配置对象。 */
    @NotNull
    private JsonNode config;

    /** 读取时的版本；不符返回 409。 */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
