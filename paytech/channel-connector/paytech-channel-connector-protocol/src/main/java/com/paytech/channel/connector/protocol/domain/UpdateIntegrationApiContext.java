package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ApiField;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 原子 API 的整体修改：主表按原版本更新，字段整组替换。 */
@Getter
@AllArgsConstructor
public class UpdateIntegrationApiContext {

    /** 已锁定并核对版本的原子 API，保存成功后同步新版本。 */
    private final ChannelApi integrationApi;

    /** 新名称。 */
    private final String name;

    /** 新说明，null 表示清空。 */
    private final String description;

    /** 规范化后的新执行定义。 */
    private final String definitionJson;

    /** 新的请求与响应字段。 */
    private final List<ApiField> fields;

    /** 操作账号。 */
    private final long updatedBy;

    /** 本次保存时间。 */
    private final Instant updateTime;
}
