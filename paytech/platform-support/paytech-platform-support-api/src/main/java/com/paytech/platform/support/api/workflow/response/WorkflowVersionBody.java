package com.paytech.platform.support.api.workflow.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 流程版本内容或摘要。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionBody {

    /** 版本号。 */
    private Integer versionNo;

    /** DRAFT 或 PUBLISHED。 */
    private String status;

    /** 流程图；摘要为空。 */
    private JsonNode graph;

    /** 启动表单；摘要为空。 */
    private JsonNode startForm;

    /** 发布时冻结的模板快照；草稿与摘要为空。 */
    private JsonNode actionSnapshot;

    /** 发布人；草稿为空。 */
    private String publishedBy;

    /** 发布时间；草稿为空。 */
    private Instant publishedTime;

    /** 最后修改时间。 */
    private Instant updateTime;
}
