package com.paytech.ledger.service.processor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.domain.view.RuleVersionView;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleRepository;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleVerRepository;
import com.paytech.ledger.service.processor.request.ActivateDueRuleVersionsRequest;
import com.paytech.ledger.service.processor.request.ActivateRuleVersionRequest;
import com.paytech.ledger.service.processor.response.ActivateDueRuleVersionsResponse;

import lombok.AllArgsConstructor;

/**
 * 一轮到期规则版本生效（XXL-Job accountingRuleVersionActivation）
 * <li>读取已到生效时间的 APPROVED 版本，按生效时间、规则集、版本号升序逐个交给 {@link ActivateRuleVersionProcessor}（同规则集资源锁、
 * 每个版本一个短事务）；同一规则集多个到期版本按生效时间依次替换。</li>
 * <li>已被当前版本取代（生效时间不晚于当前 ACTIVE 版本）的到期版本由 {@link ActivateRuleVersionProcessor} 置为 RETIRED，计入 retiredCount。</li>
 * <li>单个版本失败只记 WARN 并保持 APPROVED，下一轮重试，不阻塞其他版本；重复执行时已生效版本不再出现在到期列表中，取锁后也会重验状态。</li>
 */
@Component
@AllArgsConstructor
public class ActivateDueRuleVersionsProcessor
    extends AbstractProcessor<ActivateDueRuleVersionsRequest, ActivateDueRuleVersionsResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(ActivateDueRuleVersionsProcessor.class);

    private final ActivateRuleVersionProcessor activateRuleVersionProcessor;
    private final AccountingRuleBundleRepository accountingRuleBundleRepository;
    private final AccountingRuleBundleVerRepository accountingRuleBundleVerRepository;

    @Override
    protected ActivateDueRuleVersionsResponse processRequest(ActivateDueRuleVersionsRequest request) {
        List<AccountingRuleBundleVer> dueVersions = accountingRuleBundleVerRepository.findDueApproved(request.getAsOf());
        if (dueVersions.isEmpty()) {
            return new ActivateDueRuleVersionsResponse(0, 0, 0);
        }
        Map<Long, String> bundleCodes = accountingRuleBundleRepository.findAll().stream()
            .collect(Collectors.toMap(AccountingRuleBundle::getId, AccountingRuleBundle::getBundleCode));
        int activated = 0;
        int retired = 0;
        int failed = 0;
        for (AccountingRuleBundleVer ver : dueVersions) {
            RuleVersionStatus outcome = activate(bundleCodes.get(ver.getBundleId()), ver, request);
            if (outcome == null) {
                failed++;
            } else if (outcome == RuleVersionStatus.ACTIVE) {
                activated++;
            } else if (outcome == RuleVersionStatus.RETIRED) {
                retired++;
            }
        }
        return new ActivateDueRuleVersionsResponse(activated, retired, failed);
    }

    /**
     * 生效一个到期版本
     *
     * @return 处理后的版本状态：ACTIVE 为本次生效，RETIRED 为已被取代而退役，其他为取锁后发现已被并发处理；失败时记 WARN 并返回 null，
     *         版本保持 APPROVED 待下一轮
     */
    RuleVersionStatus activate(String bundleCode, AccountingRuleBundleVer ver, ActivateDueRuleVersionsRequest request) {
        try {
            RuleVersionView view = activateRuleVersionProcessor.process(new ActivateRuleVersionRequest(bundleCode,
                ver.getBundleVer(), request.getAsOf()));
            return view.getStatus();
        } catch (RuntimeException failure) {
            // 单个版本失败不阻塞其他版本；它保持 APPROVED，由下一轮重试。
            LOG.warn("规则版本到期生效失败: 规则集 {}, 版本 {}, {}: {}", bundleCode, ver.getBundleVer(),
                failure.getClass().getSimpleName(), failure.getMessage());
            return null;
        }
    }
}
