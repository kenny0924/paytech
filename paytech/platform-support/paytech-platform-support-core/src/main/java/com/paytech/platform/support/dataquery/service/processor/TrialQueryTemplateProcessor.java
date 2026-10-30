package com.paytech.platform.support.dataquery.service.processor;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.dataquery.domain.DataQueryResult;
import com.paytech.platform.support.dataquery.domain.DataQueryRun;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.domainservice.validator.QueryTemplateDraftValidator;
import com.paytech.platform.support.dataquery.engine.DataQueryRunner;
import com.paytech.platform.support.dataquery.service.processor.request.TrialQueryTemplateRequest;

import lombok.AllArgsConstructor;

/**
 * 试运行草稿，不保存
 * <li>复核 DATA_QUERY_TEMPLATE_MANAGE；草稿先按保存规则校验，有错误返回 422；再与执行共用参数、只读校验、分页、执行与屏蔽。</li>
 * <li>游标版本号固定为 0；不写 mor_platform，不持锁，被查库访问在事务外。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TrialQueryTemplateProcessor extends AbstractProcessor<TrialQueryTemplateRequest, DataQueryResult> {

    /** 试运行在日志与指标中的模板标识。 */
    static final String TRIAL_LABEL = "TRIAL";

    private final DataQueryRunner runner;
    private final DataSourceService dataSources;
    private final DataQueryManagementSupport support;
    private final QueryTemplateDraftValidator validator;

    @Override
    protected DataQueryResult processRequest(TrialQueryTemplateRequest request) {
        SessionContext session = support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION);
        DataSourceDetail dataSource = dataSources.findDataSource(request.getDraft().getDataSourceId());
        DataQueryManagementSupport.requireValid(validator.validate(request.getDraft(), dataSource, Map.of()));
        return runner.run(DataQueryRun.builder().templateLabel(TRIAL_LABEL).versionNo(0).content(request.getDraft())
            .dataSource(dataSource).params(request.getParams()).page(request.getPage()).size(request.getSize())
            .withTotal(request.isWithTotal()).cursor(request.getCursor()).operatorAccountId(session.account().id())
            .build());
    }
}
