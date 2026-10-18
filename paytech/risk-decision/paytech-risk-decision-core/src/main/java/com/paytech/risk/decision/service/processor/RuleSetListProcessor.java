package com.paytech.risk.decision.service.processor;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAssembler;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.RuleSetPage;
import com.paytech.risk.decision.service.processor.request.RuleSetListRequest;
import com.paytech.risk.decision.service.query.RuleSetConfigurationReader;

import lombok.AllArgsConstructor;

/** 分页读取配置头，并在同一数据库快照中判断活动发布引用。 */
@Component
@AllArgsConstructor
public class RuleSetListProcessor extends AbstractProcessor<RuleSetListRequest, RuleSetPage> {
    private final DecisionRuleSetAssembler assembler;
    private final DecisionRuleSetRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final RuleSetConfigurationReader reader;

    /** 分页结果不逐行重新读取关联，损坏的管理根立即失败。 */
    @Override
    protected RuleSetPage processRequest(RuleSetListRequest request) {
        long offset = (long)(request.getPage() - 1) * request.getSize();
        if (offset > Integer.MAX_VALUE)
            throw new InvalidParameterException("分页偏移超出范围");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> {
            var page = repository.findPage(new DecisionRuleSetRepository.RuleSetPageQuery(request.getPointCode(),
                request.getRuleSetCode(), request.getStatus(), request.getSize(), (int)offset));
            if (page.total() < 0 || page.items().size() > request.getSize())
                throw new ErrorException("Rule Set 分页计数或页大小损坏");
            Set<Long> used = reader.usedRuleSets(
                page.items().stream().map(DecisionRuleSetRepository.RuleSetSummary::managementRoot).toList(), false);
            var items = page.items().stream()
                .map(row -> assembler.toSummary(row, used.contains(row.managementRoot().getRuleSetId()))).toList();
            return new RuleSetPage(items, request.getPage(), request.getSize(), page.total());
        });
    }
}
