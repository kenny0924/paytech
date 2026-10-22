package com.paytech.platform.support.ext.rest.dataquery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.dataquery.DataQueryExecutionApi;
import com.paytech.platform.support.api.dataquery.request.ExecuteDataQueryRequestBody;
import com.paytech.platform.support.api.dataquery.response.DataQueryResultResponseBody;
import com.paytech.platform.support.dataquery.service.processor.ExecuteDataQueryProcessor;
import com.paytech.platform.support.dataquery.service.processor.request.ExecuteDataQueryRequest;
import com.paytech.platform.support.ext.rest.internalauth.InternalCallerResolver;

import lombok.AllArgsConstructor;

/**
 * 数据查询执行的协议适配；不开事务。
 * <li>调用方经 {@link InternalCallerResolver} 确定（携带已验证内部 Token 时以 Token sub 为准），白名单校验在 Processor。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DataQueryExecutionController implements DataQueryExecutionApi {

    private final DataQueryConverter converter;
    private final InternalCallerResolver internalCallers;
    private final ExecuteDataQueryProcessor processor;

    /** 解析调用方后委托执行。 */
    @Override
    public DataQueryResultResponseBody executeDataQuery(String callerService, String actor, String session,
        String templateCode, ExecuteDataQueryRequestBody request) {
        return converter.result(processor.process(new ExecuteDataQueryRequest(internalCallers.resolve(callerService),
            actor, session, templateCode, request.getParams(), request.getPage(), request.getSize(),
            request.isWithTotal(), request.getCursor())));
    }
}
