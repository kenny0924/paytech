package com.paytech.risk.decision.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.query.RuleSetHistoryView;
import com.paytech.risk.decision.domainservice.repository.DecisionConfigurationQueryRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import com.paytech.risk.decision.service.processor.request.RuleSetHistoryRequest;

import lombok.AllArgsConstructor;

/** 在 Decision 模块内编排固定上界历史查询及版本完整性校验。 */
@Component
@AllArgsConstructor
public class RuleSetHistoryProcessor
    extends AbstractProcessor<RuleSetHistoryRequest, PagingResponseBody<RuleSetHistoryView>> {

    private final DecisionRuleSetService ruleSetService;
    private final DecisionRuleSetRepository ruleSetRepository;
    private final PlatformTransactionManager transactionManager;
    private final DecisionConfigurationQueryRepository queryRepository;

    /**
     * 校验公共分页参数后，在本地只读一致性快照中加载历史事实。
     * <li>根、当前指针、总数和当前页必须处于同一提交时点，避免并发保存或删除产生矛盾响应。</li>
     */
    @Override
    protected PagingResponseBody<RuleSetHistoryView> processRequest(RuleSetHistoryRequest request) {
        PagingRequestBody paging = PagingRequestBody.of(request.getPage(), request.getSize());
        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setReadOnly(true);
        readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return readTransaction.execute(status -> readHistory(request, paging));
    }

    /** 在同一快照中确认根和版本上界，并组合当前页的历史摘要。 */
    PagingResponseBody<RuleSetHistoryView> readHistory(RuleSetHistoryRequest request, PagingRequestBody paging) {
        RuleSet root = getRuleSet(request.getRuleSetId());
        checkVersionLimit(root, request.getMaxVersionNo());

        long total =
            queryRepository.historyCount(root.getDecisionPointId(), root.getRuleSetCode(), request.getMaxVersionNo());
        if (total < 0 || total > 9007199254740991L) {
            throw new ErrorException("历史分页计数无效");
        }
        List<
            RuleSetHistoryView> items =
                queryRepository
                    .historyIds(root.getDecisionPointId(), root.getRuleSetCode(), request.getMaxVersionNo(),
                        paging.size(), paging.offset())
                    .stream().map(versionId -> getHistoryItem(root, versionId)).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 查询可查看的规则根；不存在或整体删除均按既有合同返回未找到。 */
    RuleSet getRuleSet(long ruleSetId) {
        RuleSet root =
            ruleSetRepository.findRuleSet(ruleSetId).orElseThrow(DecisionConfigurationNotFoundException::new);
        root.verifyIntegrity();
        if (root.getStatus() == RuleSetManagementStatus.DELETED) {
            throw new DecisionConfigurationNotFoundException();
        }
        return root;
    }

    /** 校验当前配置指针完整性，拒绝超出当前业务版本的浏览上界。 */
    void checkVersionLimit(RuleSet root, int maxVersionNo) {
        var current = ruleSetRepository.findVersion(root.getCurrentRuleSetVersionId())
            .orElseThrow(() -> new ErrorException("当前版本缺失"));
        root.verifyManagementHead(current);
        if (maxVersionNo > current.versionNo()) {
            throw new InvalidParameterException("历史版本上界无效");
        }
    }

    /** 只加载当前页版本，复用既有不可变 Hash/闭包校验且不检查当前目录选择资格。 */
    RuleSetHistoryView getHistoryItem(RuleSet root, long versionId) {
        var aggregate = ruleSetService.detail(versionId);
        var version = aggregate.version();
        if (version.decisionPointId() != root.getDecisionPointId()
            || !version.ruleSetCode().equals(root.getRuleSetCode())) {
            throw new ErrorException("历史所属根不一致");
        }
        return new RuleSetHistoryView(root.getRuleSetId(), versionId, aggregate.point().pointCode(),
            version.ruleSetCode(), version.ruleSetName(), version.versionNo(), version.status(),
            versionId == root.getCurrentRuleSetVersionId(), version.createdBy(), version.changeReason(),
            version.createTime());
    }
}
