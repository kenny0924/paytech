package com.paytech.risk.control.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.dal.dataobject.UnderwritingRuleVersion;
import com.paytech.risk.control.domain.underwriting.ExposureEstimate;
import com.paytech.risk.control.domain.underwriting.UnderwritingAutoRule;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingCurrent;
import com.paytech.risk.control.domain.underwriting.UnderwritingDecisionContext;
import com.paytech.risk.control.domain.underwriting.UnderwritingInput;
import com.paytech.risk.control.domain.underwriting.UnderwritingListFilter;
import com.paytech.risk.control.domain.underwriting.UnderwritingPreview;
import com.paytech.risk.control.domain.underwriting.UnderwritingRule;
import com.paytech.risk.control.domain.underwriting.UnderwritingStatistics;
import com.paytech.risk.control.domain.underwriting.UnderwritingView;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.repository.UnderwritingRuleThresholdRepository;
import com.paytech.risk.control.domainservice.repository.UnderwritingRuleVersionRepository;
import com.paytech.risk.control.service.UnderwritingService;

import lombok.AllArgsConstructor;

/** Client 承保本地保存与查询；写入以版本 CAS 与进行中 / 有效槽位唯一索引保护并发。 */
@Service
@AllArgsConstructor
public class UnderwritingServiceImpl implements UnderwritingService {
    /** 版本历史最多返回的版本数。 */
    static final int HISTORY_LIMIT = 100;

    private final PlatformTransactionManager transactionManager;
    private final ClientUnderwritingRepository underwritings;
    private final UnderwritingRuleVersionRepository ruleVersions;
    private final UnderwritingRuleThresholdRepository ruleThresholds;

    /** 单行插入，进行中槽位冲突由仓储转换为业务冲突。 */
    @Override
    public void createDraft(ClientUnderwriting row) {
        underwritings.insert(row);
    }

    /** 单行条件更新。 */
    @Override
    public void saveDraft(ClientUnderwriting row, UnderwritingContent content, Instant now) {
        underwritings.updateDraft(row, content, now);
    }

    /** 单行条件更新。 */
    @Override
    public void submitForReview(UnderwritingDecisionContext context) {
        underwritings.markSubmitted(context.getUnderwriting(), context.getContent(), context.getActorAccountId(),
            context.getNow());
    }

    /** 先释放上一有效版本的槽位再写入决定，二者同一事务提交；拒绝不影响上一有效版本。 */
    @Override
    public void decide(UnderwritingDecisionContext context) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            supersedePrevious(context);
            underwritings.decide(context.getUnderwriting(), context.getContent(), context.getVerdict());
        });
    }

    /** 先释放上一有效版本的槽位再插入 APPROVED 新行，二者同一事务提交。 */
    @Override
    public void autoApprove(UnderwritingDecisionContext context) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            supersedePrevious(context);
            underwritings.insert(context.getUnderwriting());
        });
    }

    /** 读取 ACTIVE 规则版本与其阈值。 */
    @Override
    public Optional<UnderwritingRule> findActiveRule() {
        return ruleVersions.findActive()
            .map(version -> UnderwritingRule.of(version, ruleThresholds.findByRuleVersionId(version.getRuleVersionId())));
    }

    /** 规则读取与两项纯计算。 */
    @Override
    public UnderwritingPreview preview(UnderwritingInput input, Integer settlementDays) {
        UnderwritingAutoRule autoRule = UnderwritingAutoRule.evaluate(input, findActiveRule().orElse(null));
        return new UnderwritingPreview(autoRule, ExposureEstimate.estimate(input, settlementDays), input.reviewLevel());
    }

    /** 读取单个版本并换算规则版本号。 */
    @Override
    public Optional<UnderwritingView> findView(long underwritingId) {
        return underwritings.findById(underwritingId).map(row -> UnderwritingView.of(row, ruleVersionNos().get(row.getRuleVersionId())));
    }

    /** 三次单表读取，允许观察相邻已提交状态。 */
    @Override
    public UnderwritingCurrent findCurrent(long clientId) {
        Map<Long, Integer> versionNos = ruleVersionNos();
        return new UnderwritingCurrent(view(underwritings.findEffective(clientId), versionNos),
            view(underwritings.findOpen(clientId), versionNos), view(underwritings.findLatest(clientId), versionNos));
    }

    /** 版本号倒序。 */
    @Override
    public List<UnderwritingView> findHistory(long clientId) {
        Map<Long, Integer> versionNos = ruleVersionNos();
        return underwritings.findHistory(clientId, HISTORY_LIMIT).stream()
            .map(row -> UnderwritingView.of(row, versionNos.get(row.getRuleVersionId()))).toList();
    }

    /** 列表与总数取自相邻读取，管理查询允许观察相邻已提交状态。 */
    @Override
    public Page<UnderwritingView> findPage(UnderwritingListFilter filter, int limit, long offset) {
        Map<Long, Integer> versionNos = ruleVersionNos();
        List<UnderwritingView> items = underwritings.findPage(filter, limit, offset).stream()
            .map(row -> UnderwritingView.of(row, versionNos.get(row.getRuleVersionId()))).toList();
        return new Page<>(items, underwritings.count(filter));
    }

    /** 本月为 now 所在 UTC 自然月 [月初, 下月初)。 */
    @Override
    public UnderwritingStatistics statistics(Instant now) {
        LocalDate first = now.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        return underwritings.statistics(first.atStartOfDay(ZoneOffset.UTC).toInstant(),
            first.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /** 批准时把上一有效版本置为 SUPERSEDED。 */
    void supersedePrevious(UnderwritingDecisionContext context) {
        if (context.getSuperseded() != null) {
            underwritings.supersede(context.getSuperseded(), context.getNow());
        }
    }

    /** 规则版本 ID 到版本号（规则版本只有少数几行）。 */
    Map<Long, Integer> ruleVersionNos() {
        return ruleVersions.findAll().stream()
            .collect(Collectors.toMap(UnderwritingRuleVersion::getRuleVersionId, UnderwritingRuleVersion::getVersionNo));
    }

    /** 可选版本行转换为查询结果。 */
    static UnderwritingView view(Optional<ClientUnderwriting> row, Map<Long, Integer> versionNos) {
        return row.map(value -> UnderwritingView.of(value, versionNos.get(value.getRuleVersionId()))).orElse(null);
    }
}
