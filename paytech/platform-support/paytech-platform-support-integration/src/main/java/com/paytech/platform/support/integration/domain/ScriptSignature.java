package com.paytech.platform.support.integration.domain;

import java.util.List;

import com.paytech.platform.support.api.enums.ScriptExecutionStage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 原子 API 引用公共脚本时需要的签名：编码、执行阶段与参数声明。 */
@Getter
@AllArgsConstructor
public class ScriptSignature {

    /** 脚本编码。 */
    private final String code;

    /** 执行阶段。 */
    private final ScriptExecutionStage stage;

    /** 参数声明。 */
    private final List<ParameterDeclaration> parameters;
}
