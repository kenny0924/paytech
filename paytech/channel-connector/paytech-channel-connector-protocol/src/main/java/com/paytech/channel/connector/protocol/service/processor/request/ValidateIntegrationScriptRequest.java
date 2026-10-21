package com.paytech.channel.connector.protocol.service.processor.request;

import java.util.List;

import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 只校验公共脚本配置的输入。 */
@Getter
@AllArgsConstructor
public class ValidateIntegrationScriptRequest {

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 脚本编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 执行阶段。 */
    private final ScriptExecutionStage stage;

    /** 参数声明。 */
    private final List<ParameterDeclaration> parameters;

    /** 脚本源码。 */
    private final String sourceCode;

    /** 说明。 */
    private final String description;

    /** 可选的读取版本，null 表示按新建校验。 */
    private final Integer expectedVersion;
}
