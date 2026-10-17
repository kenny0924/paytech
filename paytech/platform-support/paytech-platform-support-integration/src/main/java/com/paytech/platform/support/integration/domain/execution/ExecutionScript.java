package com.paytech.platform.support.integration.domain.execution;

import java.util.List;

import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 原子 API 引用的一个公共脚本在本次调用中的只读快照。 */
@Getter
@AllArgsConstructor
public class ExecutionScript {

    /** 脚本编码。 */
    private final String code;

    /** 执行阶段。 */
    private final ScriptExecutionStage stage;

    /** 参数声明。 */
    private final List<ParameterDeclaration> parameters;

    /** 源码。 */
    private final String sourceCode;
}
