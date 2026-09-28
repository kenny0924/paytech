package com.paytech.risk.decision.service.processor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import com.paytech.risk.decision.domain.query.FlowSummaryView;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowNodeRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRootRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.service.processor.request.FlowListRequest;

import lombok.AllArgsConstructor;

/** 在 Owner 模块内组合只读事实，保持同一 REPEATABLE_READ 快照。 */
@Component
@AllArgsConstructor
public class FlowListProcessor extends AbstractProcessor<FlowListRequest, PagingResponseBody<FlowSummaryView>> {
    private final DecisionRuleSetRepository rules;
    private final DecisionFlowNodeRepository nodes;
    private final DecisionFlowRootRepository roots;
    private final PlatformTransactionManager transactionManager;
    private final DecisionFlowVersionRepository versions;

    /** 名称筛选按 Unicode 字符数限制，保持字面包含匹配。 */
    @Override
    protected void validate(FlowListRequest request) {
        String keyword = request.getKeyword();
        if (keyword != null && (keyword.isBlank() || !keyword.equals(keyword.strip())
            || keyword.codePointCount(0, keyword.length()) > 100)) {
            throw new InvalidParameterException("keyword 必须为 1 至 100 字符且无首尾空白");
        }
    }

    /** 公共模板校验输入后进入本地只读快照，不获取资源锁或写锁。 */
    @Override
    protected PagingResponseBody<FlowSummaryView> processRequest(FlowListRequest request) {
        PagingRequestBody paging = PagingRequestBody.of(request.getPage(), request.getSize());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> read(request, paging));
    }

    /** 组合该查询需要的真实记录与目录，不改变业务事实。 */
    private PagingResponseBody<FlowSummaryView> read(FlowListRequest request, PagingRequestBody paging) {
        DecisionPoint point = point(request.getPointCode());
        List<DecisionFlowRow> selected = roots.list(point.decisionPointId(), request.getFlowCode(), request.getKeyword(),
            paging.offset(), paging.size());
        List<Long> versionIds = selected.stream().map(DecisionFlowRow::currentFlowVersionId).toList();
        Map<Long, DecisionFlowVersionRow> heads = versions.findByIds(versionIds).stream()
            .collect(Collectors.toMap(DecisionFlowVersionRow::flowVersionId, Function.identity()));
        Map<Long, Integer> counts = nodes.ruleSetNodeCounts(versionIds);
        List<FlowSummaryView> items = selected.stream().map(root -> {
            DecisionFlowVersionRow head = heads.get(root.currentFlowVersionId());
            root.verifyHead(head);
            int count = counts.getOrDefault(head.flowVersionId(), 0);
            if (count < 0 || count > 48) { throw new ErrorException("流程持久化配置不完整或身份不一致"); }
            return new FlowSummaryView(root.flowId(), point.pointCode(), root.flowCode(), head.flowName(),
                head.flowVersionId(), head.versionNo(), root.version(), count, head.createdBy(), root.updateTime());
        }).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(),
            roots.count(point.decisionPointId(), request.getFlowCode(), request.getKeyword()));
    }

    /** 可配置决策点必须是本期支持且当前有效的目录项。 */
    private DecisionPoint point(String code) {
        if (!"CHECKOUT_PREPARE".equals(code)) { throw new DecisionConfigurationNotFoundException(); }
        DecisionPoint point = rules.findPointByCode(code).orElseThrow(DecisionConfigurationNotFoundException::new);
        if (point.status() != DecisionPointStatus.ACTIVE) { throw new InvalidParameterException("决策点当前不可配置"); }
        return point;
    }
}
