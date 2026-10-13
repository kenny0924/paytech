package com.paytech.platform.support.integration.service.processor.request;

import java.util.List;

import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建公共脚本的已规范化输入。 */
@Getter
@AllArgsConstructor
public class CreateIntegrationScriptRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 脚本编码。 */
    private final String code;

    /** 名称，已去除首尾空白。 */
    private final String name;

    /** 执行阶段。 */
    private final ScriptExecutionStage stage;

    /** 参数声明。 */
    private final List<ParameterDeclaration> parameters;

    /** 脚本源码。 */
    private final String sourceCode;

    /** 说明，空白已规范为 null。 */
    private final String description;

    /** 创建原因。 */
    private final String reason;
}
