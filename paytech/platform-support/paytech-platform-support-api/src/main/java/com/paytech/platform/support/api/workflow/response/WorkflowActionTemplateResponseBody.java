package com.paytech.platform.support.api.workflow.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 动作模板。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowActionTemplateResponseBody {

    /** 模板 ID。 */
    private String templateId;

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 动作类型。 */
    private String actionType;

    /** 说明；可空。 */
    private String description;

    /** 配置；列表接口不返回。 */
    private JsonNode config;

    /** 状态。 */
    private String status;

    /** 并发版本。 */
    private Integer version;

    /** 最后修改人。 */
    private String updatedBy;

    /** 最后修改人显示名；账号不存在时为空。 */
    private String updatedByName;

    /** 最后修改时间。 */
    private Instant updateTime;
}
