package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationImplementation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道标准 API 实现的修改：按原版本替换流程定义。 */
@Getter
@AllArgsConstructor
public class UpdateImplementationContext {

    /** 已锁定并核对版本的实现，保存成功后同步新版本。 */
    private final IntegrationImplementation implementation;

    /** 规范化后的新流程定义。 */
    private final String flowDefinitionJson;

    /** 修改人。 */
    private final long updatedBy;

    /** 修改时间。 */
    private final Instant updateTime;
}
