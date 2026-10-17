package com.paytech.platform.support.integration.domainservice.function;

import java.util.Map;

/**
 * 可读取外部系统的初始化函数，只出现在流程初始化项；签名以
 * {@link com.paytech.platform.support.integration.domain.RegisteredFunctionCatalog} 为准。
 *
 * <p>在数据库事务外调用；任何失败都抛出，不返回部分结果。
 */
public interface InitializerFunction {

    /** 注册函数编码。 */
    String code();

    /**
     * 执行初始化。
     *
     * @param args 已按签名完成必填与类型检查的参数
     * @return 写入 resources.&lt;id&gt; 的结果，一级属性与注册的返回属性一致
     */
    Map<String, Object> apply(Map<String, Object> args);
}
