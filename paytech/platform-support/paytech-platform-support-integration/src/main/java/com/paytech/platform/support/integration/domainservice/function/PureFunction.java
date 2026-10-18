package com.paytech.platform.support.integration.domainservice.function;

import java.util.Map;

/**
 * 无外部 I/O 的注册函数实现；签名以 {@link com.paytech.platform.support.integration.domain.RegisteredFunctionCatalog} 为准。
 *
 * <p>参数已按签名完成必填与类型检查，缺省的可选参数不在映射中。
 */
public interface PureFunction {

    /** 注册函数编码。 */
    String code();

    /**
     * 计算函数结果。
     *
     * @param args 参数名 → 环境值
     * @return 结果，交给绑定的 result 表达式取值
     * @throws com.paytech.platform.support.integration.domain.execution.ExecutionErrorException 参数不满足函数规则
     */
    Object apply(Map<String, Object> args);
}
