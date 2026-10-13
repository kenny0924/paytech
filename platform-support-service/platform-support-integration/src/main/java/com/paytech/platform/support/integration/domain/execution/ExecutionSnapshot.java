package com.paytech.platform.support.integration.domain.execution;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次标准 API 执行所需的全部配置，来自同一只读事务并已通过运行前校验；只在本次调用内存中使用，不缓存。
 */
@Getter
@AllArgsConstructor
public class ExecutionSnapshot {

    /** 标准 API 编码。 */
    private final String standardApiCode;

    /** 接入方编码。 */
    private final String integrationCode;

    /** 标准 API 输入结构。 */
    private final FieldSchema inputSchema;

    /** 标准 API 输出结构（outputs 之下）。 */
    private final FieldSchema outputSchema;

    /** 实现流程。 */
    private final ExecutionFlow flow;

    /** 流程引用的原子 API，按编码索引。 */
    private final Map<String, ExecutionApi> apis;

    /** 这些原子 API 引用的公共脚本，按编码索引。 */
    private final Map<String, ExecutionScript> scripts;

    /** 接入方出站地址：地址代码 → 基础地址。 */
    private final Map<String, String> endpoints;
}
