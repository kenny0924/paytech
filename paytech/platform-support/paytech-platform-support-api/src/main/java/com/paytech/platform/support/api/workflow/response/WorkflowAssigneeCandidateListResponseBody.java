package com.paytech.platform.support.api.workflow.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 选人候选，按登录名排序，最多 20 条；不分页。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAssigneeCandidateListResponseBody {

    /** 候选人。 */
    private List<WorkflowAssigneeCandidateBody> items;
}
