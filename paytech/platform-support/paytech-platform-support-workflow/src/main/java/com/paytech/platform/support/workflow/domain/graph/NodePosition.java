package com.paytech.platform.support.workflow.domain.graph;

/**
 * 节点在画布上的位置，只供编辑器回显，不参与校验与运行。
 *
 * @param x 横坐标
 * @param y 纵坐标
 */
public record NodePosition(double x, double y) {
}
