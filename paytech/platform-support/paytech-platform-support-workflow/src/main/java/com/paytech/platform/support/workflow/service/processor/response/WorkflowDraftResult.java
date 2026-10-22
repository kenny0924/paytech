package com.paytech.platform.support.workflow.service.processor.response;

import java.util.List;

import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 保存草稿的内部结果。 */
@Getter
@AllArgsConstructor
public class WorkflowDraftResult {

    /** 定义 ID。 */
    private final long definitionId;

    /** 保存后定义版本。 */
    private final int version;

    /** 草稿版本号。 */
    private final int draftVersionNo;

    /** 当前校验问题。 */
    private final List<WorkflowGraphProblem> problems;
}
