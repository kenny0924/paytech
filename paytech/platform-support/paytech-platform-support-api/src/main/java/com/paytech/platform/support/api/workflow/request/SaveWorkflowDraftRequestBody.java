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

/** 保存草稿；没有草稿时从最新发布版本复制出新草稿。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaveWorkflowDraftRequestBody {

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

    /**
     * 流程图
     * <li>结构见技术方案 3.1；序列化后不超过 48 KiB，超过时拒绝保存。</li>
     */
    @NotNull
    private JsonNode graph;

    /**
     * 启动表单
     * <li>{"fields":[...]}，结构见技术方案 3.2；序列化后不超过 12 KiB，超过时拒绝保存。</li>
     */
    @NotNull
    private JsonNode startForm;

    /** 读取时流程定义的版本；不符返回 409。 */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
