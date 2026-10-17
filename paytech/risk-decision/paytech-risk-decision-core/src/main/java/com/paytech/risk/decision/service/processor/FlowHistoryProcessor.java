package com.paytech.risk.decision.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.domain.query.FlowHistoryItem;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRootRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowVersionRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.service.processor.request.FlowHistoryRequest;

import lombok.AllArgsConstructor;

/** 在 Owner 模块内组合只读事实，保持同一 REPEATABLE_READ 快照。 */
@Component
@AllArgsConstructor
public class FlowHistoryProcessor extends AbstractProcessor<FlowHistoryRequest, PagingResponseBody<FlowHistoryItem>> {
    private final DecisionFlowRootRepository roots;
    private final PlatformTransactionManager transactionManager;
    private final DecisionFlowVersionRepository versions;

    /** 公共模板校验输入后进入本地只读快照，不获取资源锁或写锁。 */
    @Override
    protected PagingResponseBody<FlowHistoryItem> processRequest(FlowHistoryRequest request) {
        PagingRequestBody paging = PagingRequestBody.of(request.getPage(), request.getSize());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> read(request, paging));
    }

    /** 组合该查询需要的真实记录与目录，不改变业务事实。 */
    private PagingResponseBody<FlowHistoryItem> read(FlowHistoryRequest request, PagingRequestBody paging) {
        DecisionFlowRow root = roots.findById(request.getFlowId()).orElseThrow(DecisionConfigurationNotFoundException::new);
        DecisionFlowVersionRow head = versions.findById(root.currentFlowVersionId())
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        root.verifyHistoryLimit(head, request.getMaxVersionNo());
        List<FlowHistoryItem> items = versions.history(root.flowId(), request.getMaxVersionNo(), paging.offset(), paging.size()).stream()
            .map(version -> {
                root.verifyVersion(version);
                return new FlowHistoryItem(version.flowVersionId(), version.versionNo(), version.flowName(),
                    version.createdBy(), version.createTime(), version.changeReason());
            }).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), versions.historyCount(root.flowId(), request.getMaxVersionNo()));
    }
}
