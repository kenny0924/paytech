package com.paytech.channel.connector.protocol.domain;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 编辑器能力目录：可选枚举、容量上限与注册函数签名，全部来自代码常量。 */
@Getter
@AllArgsConstructor
public class EditorCapabilitiesView {

    /** 枚举名到可选值，值保持声明顺序。 */
    private final Map<String, List<String>> enums;

    /** 上限名到数值。 */
    private final Map<String, Integer> limits;

    /** 已注册函数。 */
    private final List<RegisteredFunction> functions;
}
