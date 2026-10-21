package com.paytech.channel.connector.protocol.domain.verify;

import tools.jackson.databind.JsonNode;

/**
 * 一条期望的核对结果。
 *
 * @param target 核对对象：executeStatus、problem.status、problem.code、returnId 或 outputs.&lt;路径&gt;
 * @param match 匹配方式
 * @param expected 期望值；EXISTS/ABSENT 为 null
 * @param actual 实际值；不存在为 null
 * @param passed 是否满足
 */
public record VerifyAssertion(String target, String match, JsonNode expected, JsonNode actual, boolean passed) {
}
