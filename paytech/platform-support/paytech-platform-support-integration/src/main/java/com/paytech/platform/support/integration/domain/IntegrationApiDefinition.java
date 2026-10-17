package com.paytech.platform.support.integration.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已通过结构校验的原子 API 定义摘要，供列表、引用检查和流程编辑器使用；完整内容仍以 JSON 保存。 */
@Getter
@AllArgsConstructor
public class IntegrationApiDefinition {

    /** 调用参数声明。 */
    private final List<ParameterDeclaration> params;

    /** 固定值覆盖的请求字段键 &lt;LOCATION&gt;:&lt;path&gt;。 */
    private final Set<String> fixedValueKeys;

    /** HTTP 方法。 */
    private final String method;

    /** 出站地址代码。 */
    private final String endpointCode;

    /** 相对路径模板。 */
    private final String pathTemplate;

    /** 按顺序执行的脚本引用。 */
    private final List<ScriptUse> scripts;

    /** SUCCESS、FAIL 两个分支的输出声明。 */
    private final Map<String, List<ParameterDeclaration>> outputs;

    /** 去除多余空白后的规范化 JSON，用于保存与大小检查。 */
    private final String normalizedJson;
}
