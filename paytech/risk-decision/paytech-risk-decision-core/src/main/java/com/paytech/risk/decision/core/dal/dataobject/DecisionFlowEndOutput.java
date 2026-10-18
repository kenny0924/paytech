package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 放行结束节点配置的一项附加输出（t_decision_flow_end_output）；创建后不可修改。 */
@Data
public class DecisionFlowEndOutput {
    /** Snowflake 行身份。 */
    private long outputId;

    /** 所属不可变流程版本。 */
    private long flowVersionId;

    /** 同版本的放行结束节点。 */
    private long nodeId;

    /** 决策点合同声明的输出编码。 */
    private String outputCode;

    /** 该输出在合同中允许的取值。 */
    private String outputValue;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
