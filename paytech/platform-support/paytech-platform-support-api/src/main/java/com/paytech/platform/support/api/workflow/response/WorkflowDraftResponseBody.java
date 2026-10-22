package com.paytech.platform.support.api.workflow.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 保存草稿的结果：保存成功，并附当前校验问题（不阻止保存）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDraftResponseBody {

    /** 流程定义 ID。 */
    private String definitionId;

    /** 保存后流程定义的版本。 */
    private Integer version;

    /** 草稿版本号。 */
    private Integer draftVersionNo;

    /** 发布校验问题；为空表示可以发布。 */
    private List<WorkflowProblemBody> problems;
}
