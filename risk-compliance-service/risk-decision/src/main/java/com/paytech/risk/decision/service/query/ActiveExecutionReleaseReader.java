package com.paytech.risk.decision.service.query;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.configuration.DecisionRuntimeProperties;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import com.paytech.risk.decision.domainservice.service.DecisionNotConfiguredException;

import lombok.AllArgsConstructor;

/** 读取风控点在本实例环境的唯一生效执行发布。 */
@Component
@AllArgsConstructor
public class ActiveExecutionReleaseReader {
    private final DecisionRuleSetRepository points;
    private final DecisionRuntimeProperties properties;
    private final ExecutionReleaseRepository releases;

    /**
     * 读取生效发布
     *
     * @param pointCode
     *            风控点编码
     * @return 本环境的生效执行发布
     *         <li>没有生效发布抛出未配置异常；决策点未初始化属于系统错误。</li>
     */
    public ExecutionRelease require(String pointCode) {
        DecisionPoint point =
            points.findPointByCode(pointCode).orElseThrow(() -> new ErrorException("决策点未初始化: " + pointCode));
        return releases.findActive(point.decisionPointId(), properties.getEnvironment())
            .orElseThrow(() -> new DecisionNotConfiguredException(pointCode, properties.getEnvironment().getCode()));
    }
}
