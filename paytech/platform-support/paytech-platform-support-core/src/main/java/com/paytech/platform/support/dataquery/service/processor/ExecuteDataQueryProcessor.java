package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryCallerProperties;
import com.paytech.platform.support.dataquery.domain.DataQueryResult;
import com.paytech.platform.support.dataquery.service.processor.request.ExecuteDataQueryRequest;
import com.paytech.platform.support.dataquery.service.processor.request.RunDataQueryTemplateRequest;

import lombok.AllArgsConstructor;

/**
 * 按模板编码执行当前 ACTIVE 版本（technique 4.2），无锁
 * <li>调用方必须在 data-query.http.callers 中；操作人会话需具备 DATA_QUERY_EXECUTE，可执行任意 ACTIVE 模板（Q11）。</li>
 * <li>鉴权后委托 data-query 模块的 {@link RunDataQueryTemplateProcessor}：模板不存在返回 404；模板或数据源不是 ACTIVE
 * 返回 400。读取 mor_platform 不开事务，执行不写库。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ExecuteDataQueryProcessor extends AbstractProcessor<ExecuteDataQueryRequest, DataQueryResult> {

    private final DataQueryCallerProperties callers;
    private final DataQueryManagementSupport support;
    private final RunDataQueryTemplateProcessor runProcessor;

    @Override
    protected DataQueryResult processRequest(ExecuteDataQueryRequest request) {
        // 调用方与操作人
        callers.authorize(request.getCallerService());
        SessionContext session = support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.EXECUTE_PERMISSION);

        // 模板、参数、执行与结果
        return runProcessor.process(new RunDataQueryTemplateRequest(request.getTemplateCode(), request.getParams(),
            request.getPage(), request.getSize(), request.isWithTotal(), request.getCursor(), session.account().id(),
            null, request.getCallerService(), null));
    }
}
