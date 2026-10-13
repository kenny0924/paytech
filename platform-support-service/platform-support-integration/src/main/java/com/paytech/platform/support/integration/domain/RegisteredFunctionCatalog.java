package com.paytech.platform.support.integration.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 当前已确认的注册函数签名目录（研发计划第 4 节）；函数实现在第 3 步执行引擎中完成。
 *
 * <p>CHANNEL_ACCOUNT_CONFIG 签名见材料交接第 4 节；MONEY_MINOR_UNITS 按币种精度把主单位金额精确换算为最小单位整数。
 */
public final class RegisteredFunctionCatalog {

    /** 初始化函数类别。 */
    public static final String INITIALIZER = "INITIALIZER";

    /** 纯函数类别。 */
    public static final String PURE = "PURE";

    /** 全部已注册函数。 */
    public static final List<RegisteredFunction> FUNCTIONS = List.of(
        new RegisteredFunction("CHANNEL_ACCOUNT_CONFIG", INITIALIZER,
            List.of(new ParameterDeclaration("vaultToken", "STRING", true),
                new ParameterDeclaration("channelCode", "STRING", true),
                new ParameterDeclaration("channelAccountId", "STRING", true),
                new ParameterDeclaration("entityNo", "STRING", true)),
            "OBJECT{parameters: STRING 映射, credentials: PROTECTED_HANDLE 映射}",
            Map.of("parameters", "STRING", "credentials", "PROTECTED_HANDLE")),
        new RegisteredFunction("MONEY_MINOR_UNITS", PURE,
            List.of(new ParameterDeclaration("amount", "STRING", true), new ParameterDeclaration("currency", "STRING", true)),
            "INTEGER", Map.of()));

    private RegisteredFunctionCatalog() {
    }

    /** 函数可出现的位置：初始化函数只在流程初始化项；纯函数用于流程节点、出口与原子 API 输出取值。 */
    public static List<String> usages(RegisteredFunction function) {
        return INITIALIZER.equals(function.getKind()) ? List.of("FLOW_INITIALIZER")
            : List.of("FLOW_NODE", "FLOW_RETURN", "API_OUTPUT");
    }

    /** 按编码查找注册函数。 */
    public static Optional<RegisteredFunction> find(String code) {
        return FUNCTIONS.stream().filter(function -> function.getCode().equals(code)).findFirst();
    }
}
