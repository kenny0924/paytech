package com.paytech.channel.connector.ext.rest.externalapi;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.externalapi.StandardApiManagementApi;
import com.paytech.channel.connector.api.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.channel.connector.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.StandardApiResponseBody;
import com.paytech.channel.connector.api.externalapi.response.StandardApiSummary;
import com.paytech.channel.connector.protocol.service.processor.CreateStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.DeleteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.GetStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.ListStandardApisProcessor;
import com.paytech.channel.connector.protocol.service.processor.UpdateStandardApiProcessor;

import lombok.AllArgsConstructor;

/** 标准 API 管理的协议入口，规则和事务委托 protocol 模块的 Processor。 */
@RestController
@AllArgsConstructor
public class StandardApiManagementController implements StandardApiManagementApi {

    private final GetStandardApiProcessor detailProcessor;
    private final ListStandardApisProcessor listProcessor;
    private final ExternalApiManagementConverter converter;
    private final CreateStandardApiProcessor createProcessor;
    private final UpdateStandardApiProcessor updateProcessor;
    private final DeleteStandardApiProcessor deleteProcessor;

    /** 转换查询参数后委托列表用例。 */
    @Override
    public PagingResponseBody<StandardApiSummary> standardApis(Integer page,
        Integer size, String keyword) {
        return converter.toStandardApiPage(listProcessor.process(
            converter.toListRequest(page, size, keyword)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Override
    public StandardApiResponseBody standardApi(String standardApiCode) {
        return converter.toStandardApi(detailProcessor.process(converter.toGetRequest(standardApiCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Override
    public ExternalApiMutationResponseBody createStandardApi(String actor,
        CreateStandardApiRequestBody request) {
        return converter.toMutation(createProcessor.process(converter.toCreateRequest(actor, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Override
    public ExternalApiMutationResponseBody updateStandardApi(String actor, String standardApiCode,
        UpdateStandardApiRequestBody request) {
        return converter.toMutation(
            updateProcessor.process(converter.toUpdateRequest(actor, standardApiCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Override
    public ExternalApiMutationResponseBody deleteStandardApi(String actor, String standardApiCode,
        DeleteExternalApiConfigRequestBody request) {
        return converter.toMutation(
            deleteProcessor.process(converter.toDeleteRequest(actor, standardApiCode, request)));
    }
}
