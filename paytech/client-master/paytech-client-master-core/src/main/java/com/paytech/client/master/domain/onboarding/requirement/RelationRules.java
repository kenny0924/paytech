package com.paytech.client.master.domain.onboarding.requirement;

import static com.paytech.client.master.api.enums.OnboardingRequirementCode.*;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.client.master.api.enums.OnboardingRequirementCode;
import com.paytech.client.master.core.dal.dataobject.RelationType;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.client.master.domain.enums.RuleRole;
import com.paytech.client.master.domain.enums.UboCollectionMode;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.foundation.common.error.ErrorException;

/**
 * 组织关系人门槛的规则开关解释（design 10.1 注册表 UBO_* / CONTROL_PERSON_REQUIRED / MIN_SIGNATORIES / REQUIRES_*）
 * <li>只按关系类型目录的 rule_role 与 ubo_flag 计数，门槛与开关取自辖区包；不认识的关系类型不计数。</li>
 */
final class RelationRules {
    /** 兜底规则：以控制人或高级管理人员代替达到门槛的受益人。 */
    private static final String CONTROL_THEN_SMO = "CONTROL_THEN_SMO";
    /** 以控制而非持股认定受益人的依据。 */
    private static final Set<String> NON_OWNERSHIP_BASIS = Set.of("CONTROL", "SENIOR_MANAGING_OFFICIAL");
    /** 受益人兜底接受的规则角色。 */
    private static final Set<RuleRole> FALLBACK_ROLES = EnumSet.of(RuleRole.CONTROL, RuleRole.DIRECTOR, RuleRole.MANAGER);

    private final OnboardingPack pack;
    private final String organizationScope;
    private final List<RelationFact> relations;

    /** 组织类型规则按 辖区码:类型码 解析。 */
    RelationRules(OnboardingPack pack, String organizationTypeCode, List<RelationFact> relations) {
        this.pack = pack;
        this.organizationScope = pack.organizationScope(organizationTypeCode);
        this.relations = relations;
    }

    /**
     * 违反的门槛
     *
     * @return relation.<规则> 到错误码，按检查顺序
     */
    Map<String, OnboardingRequirementCode> violations() {
        Map<String, OnboardingRequirementCode> result = new LinkedHashMap<>();
        String jurisdiction = pack.jurisdictionCode();
        if (uboTotal().compareTo(new BigDecimal("100")) > 0) {
            result.put("relation.UBO_TOTAL", UBO_OWNERSHIP_EXCEEDS_100);
        }
        uboRequirement(result);
        if (pack.getRules().flag(RuleCode.CONTROL_PERSON_REQUIRED, jurisdiction) && count(RuleRole.CONTROL) == 0) {
            result.put("relation.CONTROL", CONTROL_PERSON_MISSING);
        }
        if (count(RuleRole.SIGNATORY) < pack.getRules().number(RuleCode.MIN_SIGNATORIES, organizationScope)) {
            result.put("relation.SIGNATORY", SIGNATORY_MISSING);
        }
        if (pack.getRules().flag(RuleCode.REQUIRES_DIRECTORS, organizationScope) && count(RuleRole.DIRECTOR) == 0) {
            result.put("relation.DIRECTOR", DIRECTOR_MISSING);
        }
        if (pack.getRules().flag(RuleCode.REQUIRES_MANAGER, organizationScope) && count(RuleRole.MANAGER) == 0) {
            result.put("relation.MANAGER", MANAGER_MISSING);
        }
        return result;
    }

    /** 受益人采集方式：EXEMPT 不要求；CONTROL_ONLY 只要控制人；默认要求达到门槛的受益人或兜底。 */
    void uboRequirement(Map<String, OnboardingRequirementCode> result) {
        String value = pack.getRules().value(RuleCode.UBO_COLLECTION_MODE, organizationScope);
        UboCollectionMode mode = UboCollectionMode.fromCode(value);
        if (mode == null) {
            throw new ErrorException("规则开关取值不合法: UBO_COLLECTION_MODE@" + organizationScope);
        }
        if (mode == UboCollectionMode.CONTROL_ONLY && count(RuleRole.CONTROL) == 0) {
            result.put("relation.CONTROL", CONTROL_PERSON_MISSING);
        }
        if (mode == UboCollectionMode.OWNERS_AND_CONTROL && !uboSatisfied()) {
            result.put("relation.UBO", UBO_MISSING);
        }
    }

    /** 有达到门槛（或以控制认定）的受益人，或在兜底规则下有控制人 / 高级管理人员。 */
    boolean uboSatisfied() {
        BigDecimal threshold = pack.getRules().decimal(RuleCode.UBO_THRESHOLD_PCT, pack.jurisdictionCode());
        for (RelationFact relation : relations) {
            RelationType type = pack.relationType(relation.relationTypeCode());
            if (type == null || !Boolean.TRUE.equals(type.getUboFlag())) {
                continue;
            }
            boolean byOwnership = relation.ownershipPercentage() != null && relation.ownershipPercentage().compareTo(threshold) >= 0;
            if (byOwnership || NON_OWNERSHIP_BASIS.contains(relation.uboBasis())) {
                return true;
            }
        }
        boolean fallback = CONTROL_THEN_SMO.equals(pack.getRules().value(RuleCode.UBO_FALLBACK_RULE, pack.jurisdictionCode()));
        return fallback && relations.stream().map(relation -> pack.relationType(relation.relationTypeCode()))
            .anyMatch(type -> type != null && FALLBACK_ROLES.contains(type.getRuleRole()));
    }

    /** 计入受益人的持股比例合计。 */
    BigDecimal uboTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (RelationFact relation : relations) {
            RelationType type = pack.relationType(relation.relationTypeCode());
            if (type != null && Boolean.TRUE.equals(type.getUboFlag()) && relation.ownershipPercentage() != null) {
                total = total.add(relation.ownershipPercentage());
            }
        }
        return total;
    }

    /** 承担该规则角色的不同人员数。 */
    long count(RuleRole role) {
        return relations.stream().filter(relation -> {
            RelationType type = pack.relationType(relation.relationTypeCode());
            return type != null && type.getRuleRole() == role;
        }).map(RelationFact::relatedPartyId).distinct().count();
    }
}
