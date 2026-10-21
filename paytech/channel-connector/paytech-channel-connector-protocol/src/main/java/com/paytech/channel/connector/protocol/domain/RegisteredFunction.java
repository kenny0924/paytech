package com.paytech.channel.connector.protocol.domain;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 注册函数的静态签名；实现与执行在第 3 步引擎中完成。 */
@Getter
@AllArgsConstructor
public class RegisteredFunction {

    /** 函数编码。 */
    private final String code;

    /**
     * 函数类别
     * <li>INITIALIZER：可读取外部系统，只能出现在流程初始化项。</li>
     * <li>PURE：无外部 I/O，可用于节点、原子 API 输出等取值。</li>
     */
    private final String kind;

    /** 参数声明，顺序即编辑器展示顺序。 */
    private final List<ParameterDeclaration> args;

    /** 返回值类型说明。 */
    private final String returns;

    /**
     * 返回对象的一级属性及其值类型
     * <li>初始化函数结果写入 resources.&lt;id&gt;，引用必须落在这些属性之下；纯函数为空映射。</li>
     */
    private final Map<String, String> returnRoots;
}
