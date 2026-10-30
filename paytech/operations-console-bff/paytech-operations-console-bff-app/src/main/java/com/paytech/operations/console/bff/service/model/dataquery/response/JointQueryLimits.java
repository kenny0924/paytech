package com.paytech.operations.console.bff.service.model.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 联合查询护栏；单层上限由服务端执行，跨层上限由页面执行。 */
@Getter
@Setter
public class JointQueryLimits {

    /** 入口最多输入的键值数（depth=0）。 */
    private int maxEntryValues;

    /** 每个键类型每层最多的键值数；超出部分截取并记 MAX_VALUES_PER_KEY。 */
    private int maxValuesPerKey;

    /** 每个模板每次执行最多返回的行数。 */
    private int maxRowsPerTemplate;

    /** 每层最多的模板执行数；超出的任务标 SKIPPED（STEP_EXECUTION_LIMIT）。 */
    private int maxExecutionsPerStep;

    /** 最多层数，depth 取 0..maxDepth-1；页面达到时停止并记 MAX_DEPTH。 */
    private int maxDepth;

    /** 一次联合查询累计最多的模板执行数；页面达到时停止并记 MAX_TEMPLATE_EXECUTIONS。 */
    private int maxTemplateExecutions;

    /** 一次联合查询累计最长秒数；页面达到时停止并记 MAX_TOTAL_SECONDS。 */
    private int maxTotalSeconds;
}
