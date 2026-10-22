package com.paytech.platform.support.api.workflow.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 流程定义详情：定义、草稿内容与校验问题、版本列表。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionDetailResponseBody {

    /** 定义。 */
    private WorkflowDefinitionSummaryBody definition;

    /** 草稿；没有草稿为空。 */
    private WorkflowVersionBody draft;

    /** 草稿的发布校验问题；没有草稿为空列表。 */
    private List<WorkflowProblemBody> problems;

    /** 全部版本摘要，版本号倒序。 */
    private List<WorkflowVersionBody> versions;

    /** 草稿与各版本流程图引用到的账号 ID → 显示名（登录名，没有时为脱敏邮箱）；不存在的账号不在其中。 */
    private Map<String, String> people;
}
