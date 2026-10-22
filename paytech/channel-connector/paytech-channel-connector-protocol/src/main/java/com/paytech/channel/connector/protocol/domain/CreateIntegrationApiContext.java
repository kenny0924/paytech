package com.paytech.channel.connector.protocol.domain;

import java.util.List;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ApiField;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建原子 API 的完整保存内容；标识与初始版本已在插入前准备。 */
@Getter
@AllArgsConstructor
public class CreateIntegrationApiContext {

    /** 待插入的原子 API。 */
    private final ChannelApi integrationApi;

    /** 请求与响应字段。 */
    private final List<ApiField> fields;
}
