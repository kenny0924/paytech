package com.paytech.platform.support.api.workflow.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 实例活动记录，按时间正序。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowActivityListResponseBody {

    /** 活动记录。 */
    private List<WorkflowActivityBody> items;
}
