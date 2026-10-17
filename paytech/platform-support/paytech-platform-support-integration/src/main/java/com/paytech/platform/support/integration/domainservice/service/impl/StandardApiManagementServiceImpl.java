package com.paytech.platform.support.integration.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.CreateStandardApiContext;
import com.paytech.platform.support.integration.domain.UpdateStandardApiContext;
import com.paytech.platform.support.integration.domainservice.repository.ApiFieldRepository;
import com.paytech.platform.support.integration.domainservice.repository.StandardApiRepository;
import com.paytech.platform.support.integration.domainservice.service.StandardApiManagementService;

import lombok.AllArgsConstructor;

/** 标准 API 主表与字段表在调用方事务内一起保存。 */
@Service
@AllArgsConstructor
public class StandardApiManagementServiceImpl implements StandardApiManagementService {

    /** 标准 API 拥有的两个字段方向。 */
    private static final List<ApiFieldDirection> DIRECTIONS =
        List.of(ApiFieldDirection.STANDARD_API_REQUEST, ApiFieldDirection.STANDARD_API_RESPONSE);

    private final ApiFieldRepository fields;
    private final StandardApiRepository standardApis;

    /** 先插主表再插字段；任一失败由调用方事务整体回滚。 */
    @Override
    public void create(CreateStandardApiContext context) {
        standardApis.insert(context.getStandardApi());
        fields.insertAll(context.getFields());
    }

    /** 主表 CAS 成功后删除旧字段并插入新字段，字段不单独带版本。 */
    @Override
    public void update(UpdateStandardApiContext context) {
        StandardApi standardApi = context.getStandardApi();
        standardApis.update(standardApi, context.getName(), context.getDescription(), context.getUpdatedBy(),
            context.getUpdateTime());
        fields.deleteByApi(standardApi.getStandardApiId(), DIRECTIONS);
        fields.insertAll(context.getFields());
    }

    /** 字段随标准 API 一起物理删除。 */
    @Override
    public void delete(StandardApi standardApi) {
        fields.deleteByApi(standardApi.getStandardApiId(), DIRECTIONS);
        standardApis.delete(standardApi);
    }
}
