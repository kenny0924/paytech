package com.paytech.operations.console.bff.service.model.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 本层触发的一条截断。 */
@Getter
@Setter
public class JointQueryTruncation {

    /**
     * 截断规则
     * <li>服务端产生：MAX_VALUES_PER_KEY、STEP_EXECUTION_LIMIT、STEP_BUDGET。</li>
     * <li>页面另行产生：MAX_DEPTH、MAX_TEMPLATE_EXECUTIONS、MAX_TOTAL_SECONDS、USER_STOPPED。</li>
     */
    private String rule;

    /** 涉及的键类型编码；只有 MAX_VALUES_PER_KEY 有值，其余为 null。 */
    private String keyType;

    /** 被丢弃的数量：MAX_VALUES_PER_KEY 为丢弃的键值数，STEP_EXECUTION_LIMIT、STEP_BUDGET 为跳过的执行数。 */
    private int dropped;
}
