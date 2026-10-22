package com.paytech.platform.support.workflow.domain.graph;

import com.paytech.platform.support.workflow.core.enums.InputMappingSource;

/**
 * 动作输入参数的映射。
 *
 * @param source 取值来源
 * @param value VARIABLE 时为变量名（如 start.channelCode），CONSTANT 时为常量值；可能缺失，由发布校验报告
 */
public record InputMapping(InputMappingSource source, String value) {
}
