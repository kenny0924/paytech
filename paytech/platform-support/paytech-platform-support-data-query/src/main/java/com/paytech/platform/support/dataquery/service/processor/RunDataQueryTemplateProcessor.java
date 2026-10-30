package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.DataQueryResult;
import com.paytech.platform.support.dataquery.domain.DataQueryRun;
import com.paytech.platform.support.dataquery.domain.ExecutableQueryTemplate;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.engine.DataQueryRunner;
import com.paytech.platform.support.dataquery.service.processor.request.RunDataQueryTemplateRequest;

import lombok.AllArgsConstructor;

/**
 * data-query 对 Platform 其他模块发布的执行入口（technique 8.6）：按模板编码执行当前 ACTIVE 版本，无锁
 * <li>不做会话、权限与 HTTP 调用方白名单，访问控制由调用方负责；复用只读校验、连接池、列屏蔽与日志指标。</li>
 * <li>模板不存在返回 404；模板或数据源不是 ACTIVE 返回 400。访问被查库，只能在数据库事务外调用（模板已检查）。</li>
 */
@Component
@AllArgsConstructor
public class RunDataQueryTemplateProcessor extends AbstractProcessor<RunDataQueryTemplateRequest, DataQueryResult> {

    private final DataQueryRunner runner;
    private final QueryTemplateService templates;

    @Override
    protected DataQueryResult processRequest(RunDataQueryTemplateRequest request) {
        // 加载模板、当前版本与数据源
        ExecutableQueryTemplate executable = requireExecutable(request.getTemplateCode());
        QueryTemplateDetail template = executable.getTemplate();

        // 参数、渲染、准备、执行与结果
        return runner.run(DataQueryRun.builder().templateLabel(template.getTemplateCode())
            .versionNo(template.getCurrentVersionNo()).content(template.getCurrentVersion().getContent())
            .dataSource(executable.getDataSource()).params(request.getParams()).page(request.getPage())
            .size(request.getSize()).withTotal(request.isWithTotal()).cursor(request.getCursor())
            .operatorAccountId(request.getOperatorAccountId()).timeoutCapSeconds(request.getTimeoutCapSeconds())
            .callerService(request.getCallerLabel()).rowCap(request.getRowCap()).build());
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
