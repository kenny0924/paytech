package com.paytech.platform.support.workflow.domainservice.service;

import java.time.Instant;

import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;

/** 加载实例的完整运行上下文，供运行操作与详情查询使用；只读本库。 */
public interface WorkflowInstanceQueryService {

    /**
     * 加载实例上下文。
     *
     * @param instanceId 实例 ID
     * @param now 本次操作时间
     * @return 上下文；实例不存在时为 null
     */
    WorkflowInstanceContext load(long instanceId, Instant now);
}
