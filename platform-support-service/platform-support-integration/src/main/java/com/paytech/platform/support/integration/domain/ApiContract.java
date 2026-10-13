package com.paytech.platform.support.integration.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程引用原子 API 时依赖的合同：请求字段、固定值、调用参数与输出声明。 */
@Getter
@AllArgsConstructor
public class ApiContract {

    /** 原子 API 编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 请求字段。 */
    private final List<FieldDefinition> requestFields;

    /** 固定值已覆盖的请求字段键 &lt;LOCATION&gt;:&lt;path&gt;，HEADER 名称小写。 */
    private final Set<String> fixedValueKeys;

    /** 调用参数声明。 */
    private final List<ParameterDeclaration> params;

    /** SUCCESS、FAIL 两个分支的输出声明。 */
    private final Map<String, List<ParameterDeclaration>> outputs;
}
