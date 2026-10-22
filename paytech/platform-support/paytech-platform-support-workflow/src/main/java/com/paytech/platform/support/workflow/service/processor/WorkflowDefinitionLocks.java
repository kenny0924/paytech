package com.paytech.platform.support.workflow.service.processor;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;

/** 流程定义写入口共用的锁 Key 与版本核对。 */
final class WorkflowDefinitionLocks {

    private WorkflowDefinitionLocks() {
    }

    /**
     * 定义资源锁 Key：保存草稿、发布、启停共用。
     *
     * @param definitionId 定义 ID
     * @return 锁 Key
     */
    static String lock(long definitionId) {
        return "workflow-definition:" + definitionId;
    }

    /**
     * 核对调用方读取的版本。
     *
     * @param definition 定义
     * @param expectedVersion 读取时版本
     */
    static void requireVersion(WorkflowDefinition definition, int expectedVersion) {
        if (definition.getVersion() != expectedVersion) {
            throw new ConcurrentRequestException("流程定义已被其他操作修改，请刷新后重试，当前版本 " + definition.getVersion());
        }
    }
}
