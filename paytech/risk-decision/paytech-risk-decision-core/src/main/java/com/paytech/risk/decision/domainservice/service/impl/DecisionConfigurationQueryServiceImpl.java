package com.paytech.risk.decision.domainservice.service.impl;

import java.util.*;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.*;
import com.paytech.foundation.web.paging.*;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.query.DecisionConfigurationQueries.*;
import com.paytech.risk.decision.domain.query.RejectionReasonReferencesContext;
import com.paytech.risk.decision.domain.query.SubjectTagReferencesContext;
import com.paytech.risk.decision.domainservice.repository.*;
import com.paytech.risk.decision.domainservice.service.*;

import lombok.AllArgsConstructor;

/** 同一查询事务中的发布闭包和批量字典反查。 */
@Service
@AllArgsConstructor
public class DecisionConfigurationQueryServiceImpl implements DecisionConfigurationQueryService {
    private final DecisionRuleSetRepository roots;
    private final DecisionConfigurationQueryRepository queries;

    /** 在当前查询快照中反查精确保存项，并批量补充各版本活动环境。 */
    @Override
    public PagingResponseBody<DictionaryReferenceView> subjectTagReferences(SubjectTagReferencesContext context) {
        String code = context.getTag().getTagCode();
        String scope = context.getScope();
        DictionaryType type = DictionaryType.SUBJECT_TAG;
        long total = count(queries.dictionaryReferenceCount(type, code, scope));
        var rows = queries.dictionaryReferences(type, code, scope, context.getSize(), context.offset());
        return new PagingResponseBody<>(dictionaryItems(type, rows), context.getPage(), context.getSize(), total);
    }

    /** 统一分页之后只对其中规则来源批量核对活动发布；不在两个来源中分别分页。 */
    @Override
    public PagingResponseBody<ReasonReferenceView> reasonReferences(RejectionReasonReferencesContext context) {
        String code = context.getReason().getReasonCode();
        String scope = context.getScope();
        long total = count(queries.reasonReferenceCount(code, scope));
        List<RejectionReasonReferenceRow> rows = queries.reasonReferences(code, scope, context.getSize(), context.offset());
        List<DictionaryRuleReference> ruleRows = rows.stream().filter(r -> "RULE_SET".equals(r.getSourceType())).map(r -> {
            r.verifyRuleSource();
            return new DictionaryRuleReference(r.getSourceRowId(), r.getRuleSetId(), r.getPointCode(), r.getRuleSetCode(), r.getRuleSetName(),
                r.getRuleSetVersionId(), r.getVersionNo(), r.getRuleCode(), r.getRuleName(), null, null, r.getRuleSetStatus(), r.isCurrentConfiguration());
        }).toList();
        var ruleItems = dictionaryItems(DictionaryType.REJECTION_REASON, ruleRows).iterator();
        List<ReasonReferenceView> items = rows.stream().map(r -> {
            if ("RULE_SET".equals(r.getSourceType())) {
                return (ReasonReferenceView) new RuleReasonReferenceView(ruleItems.next());
            }
            r.verifyFlowSource();
            return (ReasonReferenceView) new FlowEndReasonReferenceView(r.getPointCode(), r.getFlowId(), r.getFlowCode(), r.getFlowName(),
                r.getFlowVersionId(), r.getFlowVersionNo(), r.getNodeKey(), r.getNodeName(), r.getFlowStatus(), r.isCurrentConfiguration());
        }).toList();
        return new PagingResponseBody<>(items, context.getPage(), context.getSize(), total);
    }

    /** 批量核对规则根和发布闭包，合并当前页精确版本的活动环境。 */
    private List<DictionaryReferenceView> dictionaryItems(DictionaryType type, List<DictionaryRuleReference> rows) {
        Set<Long> checkedPoints = new HashSet<>();
        for (long rootId : rows.stream().map(DictionaryRuleReference::getRuleSetId).distinct().toList()) {
            RuleSet root = root(rootId);
            verifyReleaseReferences(root);
            if (checkedPoints.add(root.getDecisionPointId())) {
                verifyPublicationClosure(root.getDecisionPointId());
            }
        }
        var environments = new HashMap<Long, Set<DecisionEnvironment>>();
        if (!rows.isEmpty()) {
            for (var ref : queries.activeReferences(rows.stream().map(DictionaryRuleReference::getRuleSetVersionId).distinct().toList())) {
                ref.verifyIntegrity();
                environments.computeIfAbsent(ref.getRuleSetVersionId(),
                    ignored -> new TreeSet<>(Comparator.comparing(DecisionEnvironment::getCode)))
                    .add(ref.getEnvironmentCode());
            }
        }
        return rows.stream().map(row -> {
            row.verifyIntegrity(type);
            return new DictionaryReferenceView(row.getRuleSetId(), row.getPointCode(), row.getRuleSetCode(), row.getRuleSetName(), row.getRuleSetVersionId(),
                row.getVersionNo(), row.getRuleCode(), row.getRuleName(), row.getActionSequenceNo(), row.getTargetBindingCode(), row.getRuleSetStatus(),
                row.isCurrentConfiguration(), type, List.copyOf(environments.getOrDefault(row.getRuleSetVersionId(), Set.of())));
        }).toList();
    }
    /** 验证字典反查的规则根，保留已删除根的历史引用。 */
    private RuleSet root(long id) {
        RuleSet root = roots.findRuleSet(id).orElseThrow(DecisionConfigurationQueryServiceImpl::missing);
        root.verifyIntegrity();
        return root;
    }
    /** 沿用规则集整体删除的活动引用判据，检查全部环境和版本。 */
    private void verifyReleaseReferences(RuleSet root) {
        for (var ref : roots.releaseReferences(List.of(root.getDecisionPointId()), List.of(root.getRuleSetId()), false)) {
            ref.verifyIntegrity();
        }
    }
    /** 检查决策点发布关系是否断裂，损坏时拒绝给出使用关系。 */
    private void verifyPublicationClosure(long pointId) {
        if (queries.brokenReleaseCount(pointId) != 0) {
            throw new ErrorException("发布引用闭包损坏");
        }
    }
    /** 校验引用计数可由浏览器安全整数表示。 */
    private static long count(long count) {
        if (count < 0 || count > 9007199254740991L) {
            throw new ErrorException("引用分页计数无效");
        }
        return count;
    }
    /** 构造规则配置未找到结果，避免泄露其他根的版本归属。 */
    private static DecisionConfigurationNotFoundException missing() { return new DecisionConfigurationNotFoundException(); }
}
