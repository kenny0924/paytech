package com.paytech.risk.decision.domainservice.service.impl;

import static com.paytech.risk.decision.domainservice.service.DictionaryConflictException.Reason.*;

import java.util.*;

import org.springframework.stereotype.Service;

import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.core.dal.dataobject.RuleSubjectTag;
import com.paytech.risk.decision.domain.dictionary.RejectionReasonChangeContext;
import com.paytech.risk.decision.domain.dictionary.SubjectTagChangeContext;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.DictionaryType;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleView;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.*;
import com.paytech.risk.decision.domainservice.repository.RuleRejectReasonRepository;
import com.paytech.risk.decision.domainservice.repository.RuleSubjectTagRepository;
import com.paytech.risk.decision.domainservice.service.DecisionDictionaryService;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;

import lombok.AllArgsConstructor;

/** 两张当前字典表的业务规则及精确旧项沿用判据。 */
@Service
@AllArgsConstructor
public class DecisionDictionaryServiceImpl implements DecisionDictionaryService {
    private final RuleSubjectTagRepository tagRepository;
    private final RuleRejectReasonRepository reasonRepository;

    /** 保存 Processor 已构造的初始原因事实，插入失败直接传播并回滚调用方事务。 */
    @Override
    public void createRejectionReason(RuleRejectReason reason) {
        reasonRepository.insert(reason);
    }

    /** 保存新标签，失败由调用方事务回滚。 */
    @Override
    public void createSubjectTag(RuleSubjectTag tag) {
        tagRepository.insert(tag);
    }

    /** 保存已校验原因变更，Repository 在 SQL 成功后同步原对象。 */
    @Override
    public void saveRejectionReasonChange(RejectionReasonChangeContext context) {
        reasonRepository.update(context.getReason(), context.getContent(), context.getStatus(),
            context.getActorAccountId(), context.getChangedTime());
    }

    /** 保存已校验标签变更，状态与内容在同一行原子更新。 */
    @Override
    public void saveSubjectTagChange(SubjectTagChangeContext context) {
        tagRepository.update(context.getTag(), context.getContent(), context.getStatus(),
            context.getActorAccountId(), context.getChangedTime());
    }

    /** 只锁新增或修改选择的字典；已持久化的精确旧项不从客户端标志推导。 */
    @Override public void validateSelections(NormalizedRuleSet draft, DecisionRuleSetAggregate baseline) {
        Map<String, RuleView> previous = new HashMap<>();
        Map<TagSelection, Integer> allowance = new HashMap<>();
        if (baseline != null) for (RuleView rule : baseline.rules()) {
            previous.put(rule.ruleCode(), rule);
            rule.additionalActions().forEach(action -> countTags(allowance, rule.ruleCode(), action.configuration()));
        }
        var required = new TreeSet<Selection>(Comparator.comparing((Selection s) -> s.type().getCode()).thenComparing(Selection::code));
        for (NormalizedRule rule : draft.rules()) {
            if (rule.action() != null && "DENY_CHECKOUT".equals(rule.action().actionCode())) {
                RuleView old = previous.get(rule.ruleCode());
                boolean retained = old != null && old.action() != null && old.action().action().equals(rule.action())
                    && Objects.equals(old.reasonCode(), rule.reasonCode()) && Objects.equals(old.reasonMessage(), rule.reasonMessage());
                if (!retained) required.add(new Selection(DictionaryType.REJECTION_REASON, rule.reasonCode()));
            }
            Map<TagSelection, Integer> selected = new HashMap<>();
            if (rule.additionalActions() != null) rule.additionalActions().forEach(a -> countTags(selected, rule.ruleCode(), a));
            selected.forEach((key, count) -> {
                if (count > allowance.getOrDefault(key, 0)) required.add(new Selection(DictionaryType.SUBJECT_TAG, key.code()));
            });
        }
        for (Selection selection : required) {
            verifySelection(selection);
        }
    }
    /** 按规则、动作版本、主体绑定与标签计算旧项额度，忽略动作重排。 */
    private static void countTags(Map<TagSelection, Integer> values, String ruleCode, AdditionalAction action) {
        if (!"ADD_RISK_TAG".equals(action.action().actionCode())) return;
        // 一次动作内同码只算一个选择，动作重排不改变兼容额度。
        Set<String> codes = new HashSet<>();
        for (ActionParameter parameter : action.parameters()) {
            if ("TAG".equals(parameter.parameterCode()) && parameter.sourceType() == OperandSource.LITERAL
                && parameter.literalValue() instanceof StringLiteral literal) codes.add(literal.value());
        }
        codes.forEach(code -> values.merge(new TagSelection(ruleCode, action.action().actionCode(),
            action.action().versionNo(), action.targetBindingCode(), code), 1, Integer::sum));
    }
    private record Selection(DictionaryType type, String code) { }
    private record TagSelection(String ruleCode, String actionCode, int actionVersion, String target, String code) { }

    /** 按字典类型锁定真实记录，校验完整性及当前选择资格。 */
    private void verifySelection(Selection selection) {
        DictionaryStatus status;
        if (selection.type() == DictionaryType.REJECTION_REASON) {
            var reason = reasonRepository.lockForSelection(selection.code())
                .orElseThrow(() -> new DictionaryConflictException(SELECTION_UNAVAILABLE));
            reason.verifyIntegrity();
            status = reason.getStatus();
        } else {
            var tag = tagRepository.lockForSelection(selection.code())
                .orElseThrow(() -> new DictionaryConflictException(SELECTION_UNAVAILABLE));
            tag.verifyIntegrity();
            status = tag.getStatus();
        }
        if (status != DictionaryStatus.ACTIVE) {
            throw new DictionaryConflictException(SELECTION_UNAVAILABLE);
        }
    }
}
