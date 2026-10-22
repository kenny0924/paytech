package com.paytech.platform.support.workflow.domain.graph;

/**
 * 流程图连线。
 *
 * @param key 连线 key，版本内唯一
 * @param from 起点节点 key
 * @param to 终点节点 key
 */
public record WorkflowEdge(String key, String from, String to) {
}
