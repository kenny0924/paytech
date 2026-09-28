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
import com.paytech.risk.decision.core.dal.dataobject.DecisionPublicationReference;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.query.RuleSetReleaseReferenceView;
import com.paytech.risk.decision.domain.query.RuleSetReleaseReferencesView;
import com.paytech.risk.decision.domainservice.repository.DecisionConfigurationQueryRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.service.processor.request.RuleSetReleaseReferencesRequest;

import lombok.AllArgsConstructor;

/** 在 Decision 模块内组合规则集的全量使用事实和发布引用分页。 */
@Component
@AllArgsConstructor
public class RuleSetReleaseReferencesProcessor
    extends AbstractProcessor<RuleSetReleaseReferencesRequest, RuleSetReleaseReferencesView> {

    private final DecisionRuleSetRepository ruleSetRepository;
    private final PlatformTransactionManager transactionManager;
    private final DecisionConfigurationQueryRepository queryRepository;

    /**
     * 校验分页后，在同一本地只读快照中查询使用事实和发布引用。
     * <li>根、活动占槽、完整性检查、总数和当前页必须一致，避免并发发布或删除产生矛盾响应。</li>
     */
    @Override
    protected RuleSetReleaseReferencesView processRequest(RuleSetReleaseReferencesRequest request) {
        PagingRequestBody paging = PagingRequestBody.of(request.getPage(), request.getSize());
        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setReadOnly(true);
        readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return readTransaction.execute(status -> readReferences(request, paging));
    }

    /** 先验证整体使用事实，再按独立筛选读取当前页，不将空页解释为未使用。 */
    RuleSetReleaseReferencesView readReferences(RuleSetReleaseReferencesRequest request, PagingRequestBody paging) {
        RuleSet root = getRuleSet(request.getRuleSetId());
        checkVersionOwnership(root, request.getRuleSetVersionId());
        boolean inUse = isInUse(root);
        if (queryRepository.brokenReleaseCount(root.getDecisionPointId()) != 0) {
            throw new ErrorException("发布引用闭包损坏");
        }

        long total = queryRepository.releaseCount(root.getRuleSetId(), request.getEnvironmentCode(),
            request.getStatus(), request.getRuleSetVersionId());
        if (total < 0 || total > 9007199254740991L) {
            throw new ErrorException("引用分页计数无效");
        }
        List<RuleSetReleaseReferenceView> items = queryRepository
            .releases(root.getRuleSetId(), request.getEnvironmentCode(), request.getStatus(),
                request.getRuleSetVersionId(), paging.size(), paging.offset())
            .stream().map(this::toReferenceView).toList();
        return new RuleSetReleaseReferencesView(root.getRuleSetId(), inUse,
            new PagingResponseBody<>(items, paging.page(), paging.size(), total));
    }

    /** 查询并校验规则根；不存在或整体删除按既有合同返回未找到。 */
    RuleSet getRuleSet(long ruleSetId) {
        RuleSet root =
            ruleSetRepository.findRuleSet(ruleSetId).orElseThrow(DecisionConfigurationNotFoundException::new);
        root.verifyIntegrity();
        if (root.getStatus() == RuleSetManagementStatus.DELETED) {
            throw new DecisionConfigurationNotFoundException();
        }
        return root;
    }

    /** 精确版本筛选必须属于当前根，不向调用方暴露其他根的归属信息。 */
    void checkVersionOwnership(RuleSet root, Long ruleSetVersionId) {
        if (ruleSetVersionId == null) {
            return;
        }
        var version =
            ruleSetRepository.findVersion(ruleSetVersionId).orElseThrow(DecisionConfigurationNotFoundException::new);
        if (!root.ownsVersion(version)) {
            throw new DecisionConfigurationNotFoundException();
        }
    }

    /** 检查全部活动引用的占槽及关联完整性后，判断全根是否被使用。 */
    boolean isInUse(RuleSet root) {
        boolean inUse = false;
        for (var reference : ruleSetRepository.releaseReferences(List.of(root.getDecisionPointId()),
            List.of(root.getRuleSetId()), false)) {
            reference.verifyIntegrity();
            if (Long.valueOf(root.getRuleSetId()).equals(reference.getRuleSetId())) {
                inUse = true;
            }
        }
        return inUse;
    }

    /** 校验当前页持久化投影，并转换为可供 Web 使用的领域查询结果。 */
    RuleSetReleaseReferenceView toReferenceView(DecisionPublicationReference reference) {
        reference.verifyIntegrity();
        return new RuleSetReleaseReferenceView(reference.getReleaseId(), reference.getReleaseNo(),
            reference.getEnvironmentCode(), reference.getStatus(), reference.getPointCode(),
            reference.getPointVersionId(), reference.getPointVersionNo(), reference.getRuleSetVersionId(),
            reference.getVersionNo(), reference.getActivatedTime(), reference.getSupersededTime());
    }
}
