package com.paytech.channel.connector.protocol.service.processor.request;

import java.util.List;

import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改公共脚本的已规范化输入。 */
@Getter
@AllArgsConstructor
public class UpdateIntegrationScriptRequest {

    /** 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）。 */
    private final long actorId;

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 路径中的脚本编码。 */
    private final String code;

    /** 新名称。 */
    private final String name;

    /** 新执行阶段。 */
    private final ScriptExecutionStage stage;

    /** 新参数声明。 */
    private final List<ParameterDeclaration> parameters;

    /** 新源码。 */
    private final String sourceCode;

    /** 新说明，null 表示清空。 */
    private final String description;

    /** 调用方读取的版本。 */
    private final int expectedVersion;

    /** 修改原因。 */
    private final String reason;
}
