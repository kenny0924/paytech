package com.paytech.channel.connector.api.serviceexecution.response;

import lombok.Getter;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 服务渠道标准 API 的执行结果；不返回节点轨迹、原始报文或初始化结果。 */
@Getter
@Setter
public class ServiceChannelExecutionResponseBody {
    /**
     * 执行状态
     * <li>SUCCESS、FAIL、ERROR，由流程出口决定；ERROR 不能据此判定外部未执行。</li>
     */
    private String executeStatus;

    /**
     * 标准 API 输出
     * <li>按标准 API 响应字段返回；ERROR 时为空对象。</li>
     */
    private JsonNode outputs;
}
