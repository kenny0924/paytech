package com.paytech.channel.connector.protocol.domain.verify;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 验证用例的一个步骤（沙箱验证技术方案第 4 节）：标准 API、输入模板与期望；由已通过校验的 steps_json 解析。 */
@Getter
@AllArgsConstructor
public class VerifyStep {

    /** 用例内唯一的步骤 ID，供后续步骤模板引用。 */
    private final String id;

    /** 执行的标准 API 编码。 */
    private final String standardApiCode;

    /** 输入模板，字符串值中可含 ${...} 变量。 */
    private final JsonNode input;

    /** 期望的执行状态；与 problemStatus 二选一。 */
    private final String executeStatus;

    /** 期望在执行前返回的问题 HTTP 状态；与 executeStatus 二选一。 */
    private final Integer problemStatus;

    /** 期望的问题代码；可为空。 */
    private final String problemCode;

    /** 期望到达的出口 ID；可为空。 */
    private final String returnId;

    /** 输出期望。 */
    private final List<OutputExpectation> outputs;

    /**
     * 一条输出期望。
     *
     * @param path 标准 API 响应字段的点分路径
     * @param match 匹配方式
     * @param value 期望值：EQUALS 任意 JSON 值，REGEX 字符串，ONE_OF 数组，EXISTS/ABSENT 为空
     */
    public record OutputExpectation(String path, VerifyMatch match, JsonNode value) {
    }
}
