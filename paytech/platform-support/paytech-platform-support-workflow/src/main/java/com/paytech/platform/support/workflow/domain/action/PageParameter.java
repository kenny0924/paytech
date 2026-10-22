package com.paytech.platform.support.workflow.domain.action;

/**
 * 页面任务跳转时携带的一个查询参数。
 *
 * @param key 参数 key，同时是节点动作 inputs 的映射键
 * @param label 显示名
 */
public record PageParameter(String key, String label) {
}
