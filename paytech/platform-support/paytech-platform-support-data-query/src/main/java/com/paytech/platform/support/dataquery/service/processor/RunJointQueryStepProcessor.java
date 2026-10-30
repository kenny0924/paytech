package com.paytech.platform.support.dataquery.service.processor;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.dataquery.domain.JointQueryGraph;
import com.paytech.platform.support.dataquery.domain.JointQueryStep;
import com.paytech.platform.support.dataquery.domain.JointQueryStepResult;
import com.paytech.platform.support.dataquery.domainservice.service.JointQueryGraphLoader;
import com.paytech.platform.support.dataquery.domainservice.validator.JointQueryStepValidator;
import com.paytech.platform.support.dataquery.engine.JointQueryStepRunner;
import com.paytech.platform.support.dataquery.service.processor.request.RunJointQueryStepRequest;

import lombok.AllArgsConstructor;

/**
 * data-query 对 Platform 其他模块发布的联合查询单步入口，无锁
 * <li>不做会话、权限与 HTTP 调用方白名单，访问控制由调用方负责。</li>
 * <li>现读图（只读 mor_platform，不开事务）→ 校验并规范化本层键值与已加载对象（422）→ 在事务外并行执行本层参与模板；不写任何表。</li>
 */
@Component
@AllArgsConstructor
public class RunJointQueryStepProcessor extends AbstractProcessor<RunJointQueryStepRequest, JointQueryStepResult> {

    private final JointQueryStepRunner runner;
    private final JointQueryGraphLoader loader;
    private final JointQueryStepValidator validator;

    @Override
    protected JointQueryStepResult processRequest(RunJointQueryStepRequest request) {
        // 现读图并校验本层键值
        JointQueryGraph graph = loader.loadGraph();
        var keys = validator.validate(request.getDepth(), request.getKeys(), graph);
        var loaded = validator.validateLoaded(request.getLoadedSubjects(), graph);

        // 执行本层
        return runner.run(graph, new JointQueryStep(request.getDepth(), keys,
            request.getTemplateCodes() == null ? null : Set.copyOf(request.getTemplateCodes()), loaded,
            request.getOperatorAccountId()));
    }
}
