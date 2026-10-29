package com.paytech.platform.support.workflow.domain.validation;

import java.util.Map;
import java.util.Set;

/**
 * DATA_QUERY 配置校验所需的 data-query 模板事实，由调用方在校验前从 data-query 模板签名读取（只读本库）。
 *
 * @param templateCode 模板编码
 * @param active 模板与其当前版本数据源都是 ACTIVE
 * @param parameterRequired 参数名到是否必须由调用方提供（声明必填且无默认值）
 * @param declaredColumns 当前版本声明的出参列名（小写）；没有声明时为空集合
 * @param maskedColumns 其中配置了屏蔽规则的列名（小写）
 */
public record DataQueryTemplateFact(String templateCode, boolean active, Map<String, Boolean> parameterRequired,
    Set<String> declaredColumns, Set<String> maskedColumns) {

    /** 复制集合，保持不可变。 */
    public DataQueryTemplateFact {
        parameterRequired = Map.copyOf(parameterRequired);
        declaredColumns = Set.copyOf(declaredColumns);
        maskedColumns = Set.copyOf(maskedColumns);
    }
}
