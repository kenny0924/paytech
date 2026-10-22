package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 公共脚本修改：按原版本整体更新。 */
@Getter
@AllArgsConstructor
public class UpdateIntegrationScriptContext {

    /** 已锁定并核对版本的脚本，保存成功后同步新版本。 */
    private final ChannelScript integrationScript;

    /** 新名称。 */
    private final String name;

    /** 新执行阶段。 */
    private final ScriptExecutionStage stage;

    /** 规范化后的参数声明。 */
    private final String parametersJson;

    /** 新源码。 */
    private final String sourceCode;

    /** 新说明，null 表示清空。 */
    private final String description;

    /** 操作账号。 */
    private final long updatedBy;

    /** 本次保存时间。 */
    private final Instant updateTime;
}
