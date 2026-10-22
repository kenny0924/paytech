package com.paytech.platform.support.api.workflow.response;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 可发起流程的当前发布版本：启动表单、流程图（含默认人员）、模板快照与人员显示名。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowLaunchResponseBody {

    /** 流程定义。 */
    private WorkflowDefinitionSummaryBody definition;

    /** 当前发布版本号；发起时原样回传。 */
    private Integer versionNo;

    /** 流程图，节点上的审批人与执行人即默认人选。 */
    private JsonNode graph;

    /** 启动表单。 */
    private JsonNode startForm;

    /** 发布时冻结的动作模板快照。 */
    private JsonNode actionSnapshot;

    /** 图中出现的人员账号到显示名（登录名，没有时为脱敏邮箱）。 */
    private Map<String, String> people;
}
