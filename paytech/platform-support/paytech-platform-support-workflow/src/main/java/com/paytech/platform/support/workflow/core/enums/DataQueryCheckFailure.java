package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * DATA_QUERY 检查失败码（check_result=FAILED 时写入 check_detail）。
 * <li>消费者：operations-console-web 按 workflow.dataQuery.failure.&lt;CODE&gt; 显示原因。</li>
 */
@Getter
@RequiredArgsConstructor
public enum DataQueryCheckFailure implements CodeEnum {

    /** 查询模板不存在，或模板、数据源不是 ACTIVE。 */
    TEMPLATE_UNAVAILABLE("TEMPLATE_UNAVAILABLE"),

    /** 参数不合法（缺失必填、类型或约束不符）。 */
    PARAMETER_INVALID("PARAMETER_INVALID"),

    /** 查询超时。 */
    QUERY_TIMEOUT("QUERY_TIMEOUT"),

    /** 数据源无法连接或取不到连接。 */
    DATA_SOURCE_UNAVAILABLE("DATA_SOURCE_UNAVAILABLE"),

    /** 结果超过字节上限。 */
    RESULT_TOO_LARGE("RESULT_TOO_LARGE"),

    /** 结果不符合单行约定（多行、缺 satisfied、取值或输出类型不符）。 */
    RESULT_SHAPE_INVALID("RESULT_SHAPE_INVALID"),

    /** 其他查询失败（SQL 执行错误等）。 */
    QUERY_FAILED("QUERY_FAILED");

    private final String code;
}
