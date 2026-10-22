package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryCallerProperties;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.DataQueryResult;
import com.paytech.platform.support.dataquery.domain.DataQueryRun;
import com.paytech.platform.support.dataquery.domain.ExecutableQueryTemplate;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.engine.DataQueryRunner;
import com.paytech.platform.support.dataquery.service.processor.request.ExecuteDataQueryRequest;

import lombok.AllArgsConstructor;

/**
 * 按模板编码执行当前 ACTIVE 版本（technique 4.2），无锁
 * <li>调用方必须在 data-query.http.callers 中；操作人会话需具备 DATA_QUERY_EXECUTE，可执行任意 ACTIVE 模板（Q11）。</li>
 * <li>模板不存在返回 404；模板或数据源不是 ACTIVE 返回 400。读取 mor_platform 不开事务，执行不写库。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ExecuteDataQueryProcessor extends AbstractProcessor<ExecuteDataQueryRequest, DataQueryResult> {

    private final DataQueryRunner runner;
    private final QueryTemplateService templates;
    private final DataQueryCallerProperties callers;
    private final DataQueryManagementSupport support;

    @Override
    protected DataQueryResult processRequest(ExecuteDataQueryRequest request) {
        // 调用方与操作人
        callers.authorize(request.getCallerService());
        SessionContext session = support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.EXECUTE_PERMISSION);

        // 加载模板、当前版本与数据源
        ExecutableQueryTemplate executable = requireExecutable(request.getTemplateCode());
        QueryTemplateDetail template = executable.getTemplate();

        // 参数、渲染、准备、执行与结果
        return runner.run(DataQueryRun.builder().templateLabel(template.getTemplateCode())
            .versionNo(template.getCurrentVersionNo()).content(template.getCurrentVersion().getContent())
            .dataSource(executable.getDataSource()).params(request.getParams()).page(request.getPage())
            .size(request.getSize()).withTotal(request.isWithTotal()).cursor(request.getCursor())
            .operatorAccountId(session.account().id()).callerService(request.getCallerService()).build());
    }

    /** 模板不存在返回 404；模板或数据源不是 ACTIVE 返回 400。 */
    ExecutableQueryTemplate requireExecutable(String templateCode) {
        ExecutableQueryTemplate executable = templates.findExecutableTemplate(templateCode);
        if (executable == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (executable.getTemplate().getStatus() != QueryTemplateStatus.ACTIVE) {
            throw new BizCheckFailException("模板未启用: " + templateCode);
        }
        if (executable.getDataSource() == null || !executable.getDataSource().statusIs(DataSourceStatus.ACTIVE)) {
            throw new BizCheckFailException("模板的数据源未启用: " + templateCode);
        }
        return executable;
    }
}
