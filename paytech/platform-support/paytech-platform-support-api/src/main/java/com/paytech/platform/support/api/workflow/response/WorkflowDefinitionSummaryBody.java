package com.paytech.platform.support.api.workflow.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 流程定义摘要。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionSummaryBody {

    /** 流程定义 ID。 */
    private String definitionId;

    /** 流程编码。 */
    private String definitionCode;

    /** 流程名称。 */
    private String definitionName;

    /** 说明；可空。 */
    private String description;

    /** 状态。 */
    private String status;

    /** 当前发布版本号；未发布为空。 */
    private Integer currentVersionNo;

    /** 草稿版本号；没有草稿为空。 */
    private Integer draftVersionNo;

    /** 并发版本。 */
    private Integer version;

    /** 最后修改时间。 */
    private Instant updateTime;
}
