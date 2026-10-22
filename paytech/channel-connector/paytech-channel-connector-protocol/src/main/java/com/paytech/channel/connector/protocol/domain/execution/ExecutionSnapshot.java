package com.paytech.channel.connector.protocol.domain.execution;

import java.util.Map;
import java.util.Set;

import com.paytech.channel.connector.api.enums.ChannelType;

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

    /** 渠道编码。 */
    private final String channelCode;

    /** 渠道类型：决定执行舱（并发许可与 HTTP 客户端）与访问日志中的类型。 */
    private final ChannelType channelType;

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

    /** 渠道出站地址：地址代码 → 基础地址。 */
    private final Map<String, String> endpoints;

    /** 标记为沙箱的地址代码。 */
    private final Set<String> sandboxEndpoints;

    /** 本次加载的配置版本：standardApi、integration、implementation 为单值，apis、scripts 按编码索引；沙箱验证作为证据保存。 */
    private final Map<String, Object> configVersions;

    /** 流程节点引用的原子 API 所用的地址代码。 */
    public Set<String> usedEndpoints() {
        return apis.values().stream().map(api -> api.getDefinition().path("transport").path("endpointCode").asString(""))
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }
}
