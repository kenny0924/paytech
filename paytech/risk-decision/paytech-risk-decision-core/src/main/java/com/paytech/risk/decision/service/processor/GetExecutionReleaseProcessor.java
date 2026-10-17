package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.release.ExecutionReleaseView;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.service.processor.request.ExecutionReleaseCommandRequest;
import com.paytech.risk.decision.service.query.ExecutionReleaseViewReader;

import lombok.AllArgsConstructor;

/** 查询执行发布、静态校验与最近一次影子运行；不存在或为结账测试候选返回不存在。 */
@Component
@AllArgsConstructor
public class GetExecutionReleaseProcessor extends AbstractProcessor<ExecutionReleaseCommandRequest, ExecutionReleaseView> {
    private final ExecutionReleaseViewReader views;
    private final ExecutionReleaseRepository releases;

    @Override
    protected ExecutionReleaseView processRequest(ExecutionReleaseCommandRequest request) {
        return views.read(releases.findById(request.getReleaseId()).orElseThrow(DecisionConfigurationNotFoundException::new));
    }
}
