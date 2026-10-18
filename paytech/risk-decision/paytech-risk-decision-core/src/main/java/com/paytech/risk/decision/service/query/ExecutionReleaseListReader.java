package com.paytech.risk.decision.service.query;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionReleaseSummary;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.release.ExecutionReleasePageView;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseQueryRepository;

import lombok.AllArgsConstructor;

/** 读取执行发布分页列表；调用方负责只读事务，使总数与当前页来自同一快照。 */
@Component
@AllArgsConstructor
public class ExecutionReleaseListReader {
    private final DecisionRuleSetRepository points;
    private final ExecutionReleaseQueryRepository releases;

    /**
     * 读取一页
     *
     * @param pointCode 决策点编码；未登记视为参数错误
     * @param status 可选状态
     * @param page 页码
     * @param size 每页条数
     * @return 当前页与总数
     */
    public ExecutionReleasePageView read(String pointCode, DecisionReleaseStatus status, int page, int size) {
        DecisionPoint point = points.findPointByCode(pointCode)
            .orElseThrow(() -> new InvalidParameterException("决策点不存在: " + pointCode));
        int offset;
        try {
            offset = Math.multiplyExact(page - 1, size);
        } catch (ArithmeticException failure) {
            throw new InvalidParameterException("分页范围过大", failure);
        }
        long total = releases.count(point.decisionPointId(), status);
        return new ExecutionReleasePageView(releases.findPage(point.decisionPointId(), status, size, offset).stream()
            .map(ExecutionReleaseListReader::item).toList(), page, size, total);
    }

    /** 投影转换；生效以状态为准。 */
    static ExecutionReleasePageView.Item item(ExecutionReleaseSummary row) {
        return new ExecutionReleasePageView.Item(row.getReleaseId(), row.getReleaseNo(), row.getPointCode(),
            row.getEnvironmentCode().getCode(), row.getStatus().getCode(), row.getStatus() == DecisionReleaseStatus.ACTIVE,
            row.getFlowId(), row.getFlowCode(), row.getFlowVersionId(), row.getFlowVersionNo(), row.getContentHash(),
            row.getCreatedBy(), row.getCreateTime(), row.getActivatedBy(), row.getActivatedTime(), row.getSupersededTime());
    }
}
