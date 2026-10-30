package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryCallerProperties;
import com.paytech.platform.support.dataquery.domain.JointQueryStepResult;
import com.paytech.platform.support.dataquery.service.processor.request.ExecuteJointQueryStepRequest;
import com.paytech.platform.support.dataquery.service.processor.request.RunJointQueryStepRequest;

import lombok.AllArgsConstructor;

/**
 * 执行联合查询一层（J3），无锁
 * <li>调用方必须在 data-query.http.callers 中；操作人会话需具备 DATA_QUERY_EXECUTE（与单模板执行相同）。</li>
 * <li>鉴权后委托 data-query 模块的 {@link RunJointQueryStepProcessor}：键值不合法返回 422；单个模板失败或跳过记在节点上。
 * 读取 mor_platform 不开事务，被查库访问全部在事务外，不写任何表、不登记审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ExecuteJointQueryStepProcessor
    extends AbstractProcessor<ExecuteJointQueryStepRequest, JointQueryStepResult> {

    private final DataQueryCallerProperties callers;
    private final DataQueryManagementSupport support;
    private final RunJointQueryStepProcessor runProcessor;

    @Override
    protected JointQueryStepResult processRequest(ExecuteJointQueryStepRequest request) {
        // 调用方与操作人
        callers.authorize(request.getCallerService());
        SessionContext session = support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.EXECUTE_PERMISSION);

        // 校验、执行本层与结果
        return runProcessor.process(new RunJointQueryStepRequest(request.getDepth(), request.getKeys(),
            request.getTemplateCodes(), request.getLoadedSubjects(), session.account().id()));
    }
}
