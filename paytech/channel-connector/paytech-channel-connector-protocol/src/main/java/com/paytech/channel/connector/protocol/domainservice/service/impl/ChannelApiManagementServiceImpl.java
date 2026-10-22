package com.paytech.channel.connector.protocol.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.CreateIntegrationApiContext;
import com.paytech.channel.connector.protocol.domain.UpdateIntegrationApiContext;
import com.paytech.channel.connector.protocol.domainservice.repository.ApiFieldRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelApiRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelApiManagementService;

import lombok.AllArgsConstructor;

/** 原子 API 主表与字段表在调用方事务内一起保存。 */
@Service
@AllArgsConstructor
public class ChannelApiManagementServiceImpl implements ChannelApiManagementService {

    /** 原子 API 拥有的两个字段方向。 */
    private static final List<ApiFieldDirection> DIRECTIONS =
        List.of(ApiFieldDirection.INTEGRATION_API_REQUEST, ApiFieldDirection.INTEGRATION_API_RESPONSE);

    private final ApiFieldRepository fields;
    private final ChannelApiRepository channelApis;

    /** 先插主表再插字段；任一失败由调用方事务整体回滚。 */
    @Override
    public void create(CreateIntegrationApiContext context) {
        channelApis.insert(context.getIntegrationApi());
        fields.insertAll(context.getFields());
    }

    /** 主表 CAS 成功后删除旧字段并插入新字段。 */
    @Override
    public void update(UpdateIntegrationApiContext context) {
        ChannelApi row = context.getIntegrationApi();
        channelApis.update(row, context.getName(), context.getDescription(), context.getDefinitionJson(),
            context.getUpdatedBy(), context.getUpdateTime());
        fields.deleteByApi(row.getChannelApiId(), DIRECTIONS);
        fields.insertAll(context.getFields());
    }

    /** 字段随原子 API 一起物理删除。 */
    @Override
    public void delete(ChannelApi integrationApi) {
        fields.deleteByApi(integrationApi.getChannelApiId(), DIRECTIONS);
        channelApis.delete(integrationApi);
    }
}
