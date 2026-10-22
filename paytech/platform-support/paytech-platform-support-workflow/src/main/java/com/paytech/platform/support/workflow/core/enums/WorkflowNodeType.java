package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 流程图节点类型。 */
@Getter
@RequiredArgsConstructor
public enum WorkflowNodeType implements CodeEnum {

    /** 开始节点：恰好一个，没有入边。 */
    START("START"),

    /** 结束节点：至少一个，没有出边；到达即实例完成。 */
    END("END"),

    /** 任务节点：挂审批人或动作，只有一条出边。 */
    TASK("TASK"),

    /** 排他网关：按条件顺序选一条出边，都不满足走默认边。 */
    EXCLUSIVE("EXCLUSIVE"),

    /** 并行分叉：同时激活每条出边。 */
    PARALLEL_SPLIT("PARALLEL_SPLIT"),

    /** 并行汇合：等待成对分叉的全部分支完成。 */
    PARALLEL_JOIN("PARALLEL_JOIN");

    private final String code;
}
