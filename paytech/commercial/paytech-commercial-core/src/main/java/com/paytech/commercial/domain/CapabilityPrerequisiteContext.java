package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.CapabilityPrerequisiteCode;
import com.paytech.commercial.api.enums.CapabilityPrerequisiteResult;
import com.paytech.commercial.core.constant.CapabilityPrerequisiteReasons;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.relationship.QualificationSubject;
import com.paytech.foundation.compensation.api.CompensationEventRequest;

import lombok.Getter;
import lombok.Setter;

/**
 * 一次开通项前置核对的上下文（开通项前置门禁 technique.md §7、§9）：合同、BCR Root、开通项与条件、各商业条款方案的 ACTIVE 版本价格、
 * compliance 资格结论、渠道身份事实（REQ-02 technique.md §10.2），以及构造时一次算出的逐开通项判定
 * <li>Processor 在合同锁内、事务外读取本库事实并完成资格与渠道身份查询后一次构造；判定是纯计算，不访问数据库或外部服务。</li>
 * <li>状态规则：SUSPENDED_MANUALLY 不触碰；任一项 UNKNOWN 不迁移；全部满足 → ENABLED；ENABLED 遇明确不满足 → SUSPENDED_BY_PREREQUISITE；
 * PENDING_PREREQUISITES 不满足时保持。</li>
 * <li>root 与开通项在保存成功后由 Repository 同步为新值；事务回滚后整体丢弃。</li>
 */
@Getter
public class CapabilityPrerequisiteContext {
    /** 门禁状态迁移以系统身份记录的操作者。 */
    public static final long SYSTEM_ACTOR_ID = 0L;

    /** 等待门禁处理的执行状态：等待前置与前置失效暂停（合同 / 价格激活后需复核、5 分钟重查范围）。 */
    public static final Set<AgreementCapabilityExecutionStatus> AWAITING_PREREQUISITES = Set.of(
        AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES, AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE);

    /** 合同（CLIENT） */
    private final Agreement agreement;

    /** 合同 BCR Root（锁内读取） */
    private final BusinessChangeRecord root;

    /** 本次核对的业务时刻（UTC 微秒） */
    private final Instant now;

    /** 合同全部开通项的约束行，用于状态迁移快照 */
    private final List<AgreementCapabilityLimit> limits;

    /** 按开通项 ID 升序的逐项判定；目录缺失的开通项（数据异常）不出现 */
    private final List<CapabilityPrerequisiteDecision> decisions;

    /** 有迁移时登记的 AgreementCapabilityStatusChanged 发布任务；由 Processor 在保存前设置 */
    @Setter
    private CompensationEventRequest capabilityStatusChangedEvent;

    /**
     * 构造上下文并一次算出全部判定
     *
     * @param agreement CLIENT 合同
     * @param root 合同 BCR Root
     * @param now 业务时刻
     * @param capabilities 合同全部开通项（含目录与接受条件）
     * @param limits 这些开通项的约束行
     * @param termCoverages 合同各商业条款方案的 ACTIVE 版本价格
     * @param qualifications 每个资格主体的结论；资格查询失败时为 null
     * @param channelIdentity Client 主 MCC 与各 OE 的渠道身份就绪结论
     */
    public CapabilityPrerequisiteContext(Agreement agreement, BusinessChangeRecord root, Instant now,
        List<CapabilityAcceptance> capabilities, List<AgreementCapabilityLimit> limits, List<TermCoverage> termCoverages,
        Map<QualificationSubject, Boolean> qualifications, ChannelIdentityFacts channelIdentity) {
        this.agreement = agreement;
        this.root = root;
        this.now = now;
        this.limits = List.copyOf(limits);
        List<CapabilityPrerequisiteDecision> computed = new ArrayList<>();
        for (CapabilityAcceptance capability : capabilities) {
            computed.add(decide(capability, termCoverages, qualifications, channelIdentity));
        }
        this.decisions = List.copyOf(computed);
    }

    /**
     * 资格主体：Client 与全部成员开通项所在 OE，按出现顺序去重；没有成员开通项时为空（无需查询）
     *
     * @param agreement CLIENT 合同
     * @param capabilities 合同全部开通项
     * @return 主体
     */
    public static List<QualificationSubject> qualificationSubjects(Agreement agreement,
        List<AgreementCapability> capabilities) {
        Set<QualificationSubject> subjects = new LinkedHashSet<>();
        for (AgreementCapability capability : capabilities) {
            if (capability.getStatus() == AgreementCapabilityStatus.ACTIVE) {
                subjects.add(QualificationSubject.client(agreement.getClientId()));
                subjects.add(QualificationSubject.operatingEntity(capability.getEntityNo()));
            }
        }
        return List.copyOf(subjects);
    }

    /**
     * ④ 需要查询渠道身份的开通项：成员资格 ACTIVE、支付方式类、且价格已覆盖至少一个币种；没有时无需查询
     *
     * @param capabilities 合同全部开通项（含目录与接受条件）
     * @param termCoverages 合同各商业条款方案的 ACTIVE 版本价格覆盖
     * @return 按输入顺序的开通项
     */
    public static List<CapabilityAcceptance> channelIdentitySubjects(List<CapabilityAcceptance> capabilities,
        List<TermCoverage> termCoverages) {
        return capabilities.stream().filter(capability -> capability.member()
            && capability.kind() == CapabilityKind.PAYMENT_METHOD && !coveredCurrencies(capability, termCoverages).isEmpty())
            .toList();
    }

    /**
     * 开通项的已覆盖币种：与其种类相关、有 ACTIVE 版本的方案中价格已完整覆盖的币种并集（字典序）
     *
     * @param capability 开通项
     * @param termCoverages 合同各商业条款方案的 ACTIVE 版本价格覆盖
     * @return 已覆盖币种；没有时为空
     */
    public static List<String> coveredCurrencies(CapabilityAcceptance capability, List<TermCoverage> termCoverages) {
        long capabilityId = capability.capability().getAgreementCapabilityId();
        return termCoverages.stream()
            .filter(coverage -> coverage.relevantTo(capability.kind()) && coverage.getActiveVersion() != null)
            .flatMap(coverage -> coverage.coveredCurrencies(capabilityId).stream()).distinct().sorted().toList();
    }

    /** 需要迁移执行状态的判定。 */
    public List<CapabilityPrerequisiteDecision> changes() {
        return decisions.stream().filter(CapabilityPrerequisiteDecision::changed).toList();
    }

    /** 任一成员开通项有 UNKNOWN 结论（资格查询失败）。 */
    public boolean anyUnknown() {
        return decisions.stream().anyMatch(CapabilityPrerequisiteDecision::unknown);
    }

    /**
     * 某开通项的约束行
     *
     * @param capability 开通项
     * @return 约束行
     */
    public List<AgreementCapabilityLimit> limitsOf(AgreementCapability capability) {
        return limits.stream()
            .filter(limit -> limit.getAgreementCapabilityId().equals(capability.getAgreementCapabilityId())).toList();
    }

    /** 单个开通项的逐项结论与目标执行状态；成员资格 DISABLED 不检查、不迁移。 */
    CapabilityPrerequisiteDecision decide(CapabilityAcceptance acceptance, List<TermCoverage> termCoverages,
        Map<QualificationSubject, Boolean> qualifications, ChannelIdentityFacts channelIdentity) {
        AgreementCapability capability = acceptance.capability();
        if (!acceptance.member()) {
            return new CapabilityPrerequisiteDecision(capability, acceptance.kind(), capability.getExecutionStatus(),
                List.of());
        }
        List<CapabilityPrerequisiteDecision.Check> checks = List.of(qualification(capability, qualifications),
            agreementTerm(acceptance, termCoverages), channelIdentity(acceptance, termCoverages, channelIdentity));
        return new CapabilityPrerequisiteDecision(capability, acceptance.kind(),
            target(capability.getExecutionStatus(), checks), checks);
    }

    /** 目标执行状态：手动暂停与 UNKNOWN 不迁移；全部满足为 ENABLED；已开通遇明确不满足暂停；其余保持。 */
    static AgreementCapabilityExecutionStatus target(AgreementCapabilityExecutionStatus current,
        List<CapabilityPrerequisiteDecision.Check> checks) {
        boolean unknown = checks.stream().anyMatch(check -> check.getResult() == CapabilityPrerequisiteResult.UNKNOWN);
        if (current == AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY || unknown) {
            return current;
        }
        if (checks.stream().allMatch(CapabilityPrerequisiteDecision.Check::passed)) {
            return AgreementCapabilityExecutionStatus.ENABLED;
        }
        if (current == AgreementCapabilityExecutionStatus.ENABLED) {
            return AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE;
        }
        return current;
    }

    /** ①：Client 与开通项所在 OE 都合格；资格查询失败为 UNKNOWN。 */
    CapabilityPrerequisiteDecision.Check qualification(AgreementCapability capability,
        Map<QualificationSubject, Boolean> qualifications) {
        CapabilityPrerequisiteCode code = CapabilityPrerequisiteCode.QUALIFICATION;
        if (qualifications == null) {
            return check(code, CapabilityPrerequisiteResult.UNKNOWN, CapabilityPrerequisiteReasons.CHECK_UNAVAILABLE,
                Map.of());
        }
        QualificationSubject client = QualificationSubject.client(agreement.getClientId());
        if (!Boolean.TRUE.equals(qualifications.get(client))) {
            return unsatisfied(code, CapabilityPrerequisiteReasons.CLIENT_NOT_QUALIFIED,
                Map.of("subjectType", client.subjectType().getCode(), "subjectId", client.subjectId()));
        }
        QualificationSubject entity = QualificationSubject.operatingEntity(capability.getEntityNo());
        if (!Boolean.TRUE.equals(qualifications.get(entity))) {
            return unsatisfied(code, CapabilityPrerequisiteReasons.OPERATING_ENTITY_NOT_QUALIFIED,
                Map.of("subjectType", entity.subjectType().getCode(), "entityNo", capability.getEntityNo()));
        }
        return check(code, CapabilityPrerequisiteResult.SATISFIED, null, Map.of());
    }

    /**
     * ②：合同 ACTIVE 且当前在合同区间内；开通项区间覆盖当前；FEATURE 到此满足；其他种类须有与其种类相关的方案 ACTIVE 版本完整覆盖它
     * <li>相关方案：方案条款类型登记的计费事件挂接了该种类（PAYMENT_METHOD → CLIENT_PRICING，PAYOUT_RAIL → PAYOUT_FEE 等）。</li>
     */
    CapabilityPrerequisiteDecision.Check agreementTerm(CapabilityAcceptance acceptance, List<TermCoverage> termCoverages) {
        CapabilityPrerequisiteCode code = CapabilityPrerequisiteCode.AGREEMENT_TERM;
        if (agreement.getStatus() != AgreementStatus.ACTIVE
            || !within(agreement.getEffectiveFrom(), agreement.getEffectiveTo())) {
            return unsatisfied(code, CapabilityPrerequisiteReasons.AGREEMENT_NOT_ACTIVE,
                Map.of("agreementStatus", agreement.getStatus().getCode()));
        }
        AgreementCapability capability = acceptance.capability();
        if (!within(capability.getEffectiveFrom(), capability.getEffectiveTo())) {
            return unsatisfied(code, CapabilityPrerequisiteReasons.CAPABILITY_NOT_EFFECTIVE, Map.of());
        }
        if (acceptance.kind() == CapabilityKind.FEATURE) {
            return check(code, CapabilityPrerequisiteResult.SATISFIED, null, Map.of());
        }
        return priceCoverage(acceptance, termCoverages);
    }

    /** ② 的价格部分：任一相关方案的 ACTIVE 版本完整覆盖即满足；原因指向第一个相关方案。 */
    CapabilityPrerequisiteDecision.Check priceCoverage(CapabilityAcceptance acceptance, List<TermCoverage> termCoverages) {
        CapabilityPrerequisiteCode code = CapabilityPrerequisiteCode.AGREEMENT_TERM;
        List<TermCoverage> relevant = termCoverages.stream().filter(coverage -> coverage.relevantTo(acceptance.kind()))
            .toList();
        List<TermCoverage> active = relevant.stream().filter(coverage -> coverage.getActiveVersion() != null).toList();
        if (active.isEmpty()) {
            return unsatisfied(code, CapabilityPrerequisiteReasons.TERM_VERSION_NOT_ACTIVE, relevant.isEmpty() ? Map.of()
                : Map.of("termType", relevant.getFirst().getTermType()));
        }
        boolean covered = active.stream()
            .anyMatch(coverage -> coverage.covers(acceptance.capability().getAgreementCapabilityId()));
        return covered ? check(code, CapabilityPrerequisiteResult.SATISFIED, null, Map.of())
            : unsatisfied(code, CapabilityPrerequisiteReasons.PRICE_NOT_COVERED, priceGapParams(acceptance, active.getFirst()));
    }

    /** 价格未覆盖的文案参数：条款类型、OE；能归到具体币种时加 currencies（逗号分隔，字典序）。 */
    static Map<String, String> priceGapParams(CapabilityAcceptance acceptance, TermCoverage coverage) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("termType", coverage.getTermType());
        params.put("entityNo", acceptance.capability().getEntityNo());
        List<String> currencies = coverage.uncoveredCurrencies(acceptance.capability().getAgreementCapabilityId());
        if (!currencies.isEmpty()) {
            params.put("currencies", String.join(",", currencies));
        }
        return params;
    }

    /**
     * ④：支付方式类开通项在其 OE 下按运行期选 MID 同一口径就绪（REQ-02 technique.md §10.2）；其他种类不适用
     * <li>价格尚未覆盖任何币种时等待价格（按满足处理，② 已说明缺价格）；查询失败为 UNKNOWN；Client 主 MCC 未确认为不满足。</li>
     */
    CapabilityPrerequisiteDecision.Check channelIdentity(CapabilityAcceptance acceptance, List<TermCoverage> termCoverages,
        ChannelIdentityFacts facts) {
        CapabilityPrerequisiteCode code = CapabilityPrerequisiteCode.CHANNEL_IDENTITY;
        if (acceptance.kind() != CapabilityKind.PAYMENT_METHOD) {
            return check(code, CapabilityPrerequisiteResult.SKIPPED, CapabilityPrerequisiteReasons.NOT_APPLICABLE, Map.of());
        }
        String entityNo = acceptance.capability().getEntityNo();
        List<String> currencies = coveredCurrencies(acceptance, termCoverages);
        if (currencies.isEmpty()) {
            return check(code, CapabilityPrerequisiteResult.SKIPPED, CapabilityPrerequisiteReasons.AWAITING_PRICE,
                Map.of("entityNo", entityNo));
        }
        if (facts.unavailable(entityNo)) {
            return check(code, CapabilityPrerequisiteResult.UNKNOWN, CapabilityPrerequisiteReasons.CHECK_UNAVAILABLE, Map.of());
        }
        if (facts.mccCode() == null) {
            return unsatisfied(code, CapabilityPrerequisiteReasons.CLIENT_MCC_MISSING,
                Map.of("clientId", Long.toString(agreement.getClientId())));
        }
        return channelReadiness(acceptance, currencies, facts);
    }

    /**
     * ④ 的逐币种结论（Q-4 修订：任一币种就绪即满足）
     * <li>至少一个已覆盖币种就绪：SATISFIED；另有不就绪币种时带 CHANNEL_IDENTITY_PARTIAL 与参数，全部就绪时原因为空。</li>
     * <li>全部不就绪：UNSATISFIED，取字典序第一个不就绪币种的原因；参数 currencies 列出全部不就绪币种。</li>
     */
    static CapabilityPrerequisiteDecision.Check channelReadiness(CapabilityAcceptance acceptance, List<String> currencies,
        ChannelIdentityFacts facts) {
        CapabilityPrerequisiteCode code = CapabilityPrerequisiteCode.CHANNEL_IDENTITY;
        String entityNo = acceptance.capability().getEntityNo();
        String paymentMethodCode = acceptance.catalog().getPaymentMethodCode();
        List<ChannelIdentityReadiness> notReady = new ArrayList<>();
        for (String currency : currencies) {
            ChannelIdentityReadiness readiness = facts.readiness(entityNo, paymentMethodCode, currency);
            if (readiness == null) {
                return check(code, CapabilityPrerequisiteResult.UNKNOWN, CapabilityPrerequisiteReasons.CHECK_UNAVAILABLE,
                    Map.of());
            }
            if (!readiness.ready()) {
                notReady.add(readiness);
            }
        }
        if (notReady.isEmpty()) {
            return check(code, CapabilityPrerequisiteResult.SATISFIED, null, Map.of());
        }
        Map<String, String> params = readinessGapParams(entityNo, paymentMethodCode, facts.mccCode(), notReady);
        if (notReady.size() < currencies.size()) {
            return check(code, CapabilityPrerequisiteResult.SATISFIED, CapabilityPrerequisiteReasons.CHANNEL_IDENTITY_PARTIAL,
                params);
        }
        return unsatisfied(code, notReady.getFirst().reasonCode(), params);
    }

    /** ④ 缺口的文案参数：OE、支付方式、MCC 与不就绪币种（字典序逗号分隔）。 */
    static Map<String, String> readinessGapParams(String entityNo, String paymentMethodCode, String mccCode,
        List<ChannelIdentityReadiness> notReady) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("entityNo", entityNo);
        params.put("paymentMethodCode", paymentMethodCode);
        params.put("mccCode", mccCode);
        params.put("currencies", String.join(",", notReady.stream().map(readiness -> readiness.item().currency()).toList()));
        return params;
    }

    /** 当前时刻在半开区间 [from, to) 内；空起点视为未生效。 */
    boolean within(Instant from, Instant to) {
        return from != null && !from.isAfter(now) && (to == null || to.isAfter(now));
    }

    /** 明确不满足。 */
    static CapabilityPrerequisiteDecision.Check unsatisfied(CapabilityPrerequisiteCode code, String reasonCode,
        Map<String, String> params) {
        return check(code, CapabilityPrerequisiteResult.UNSATISFIED, reasonCode, params);
    }

    /** 构造一项结论。 */
    static CapabilityPrerequisiteDecision.Check check(CapabilityPrerequisiteCode code, CapabilityPrerequisiteResult result,
        String reasonCode, Map<String, String> params) {
        return new CapabilityPrerequisiteDecision.Check(code, result, reasonCode, params);
    }

    /**
     * 合同的一个商业条款方案：条款类型、其计费事件挂接的开通项种类、ACTIVE 版本（可空）及其价格完整覆盖的开通项
     * <li>覆盖由 Processor 以激活校验第 3 条同一口径（TermVersionActivationValidator.coversCapability）算出后传入。</li>
     */
    @Getter
    public static class TermCoverage {
        /** 条款类型 */
        private final String termType;

        /** 该条款类型计费事件挂接的开通项种类 */
        private final Set<CapabilityKind> kinds;

        /** 方案当前 ACTIVE 版本；没有时为空 */
        private final CommercialTermVersion activeVersion;

        /** ACTIVE 版本价格完整覆盖的开通项 ID；没有 ACTIVE 版本时为空 */
        private final Set<Long> coveredCapabilityIds;

        /** 未被完整覆盖的开通项 → 未覆盖的币种（字典序；无法归到币种时为空列表） */
        private final Map<Long, List<String>> uncoveredCurrencies;

        /** 成员相关开通项 → 已完整覆盖的币种（字典序；没有时为空列表），④ 只核对这些币种 */
        private final Map<Long, List<String>> coveredCurrencies;

        /**
         * 构造方案覆盖事实
         *
         * @param termType 条款类型
         * @param kinds 计费事件挂接的种类
         * @param activeVersion ACTIVE 版本，可空
         * @param coveredCapabilityIds 被完整覆盖的开通项 ID
         * @param uncoveredCurrencies 未被完整覆盖的开通项及其未覆盖的币种
         * @param coveredCurrencies 成员相关开通项及其已覆盖的币种
         */
        public TermCoverage(String termType, Set<CapabilityKind> kinds, CommercialTermVersion activeVersion,
            Set<Long> coveredCapabilityIds, Map<Long, List<String>> uncoveredCurrencies,
            Map<Long, List<String>> coveredCurrencies) {
            this.termType = termType;
            this.kinds = Set.copyOf(kinds);
            this.activeVersion = activeVersion;
            this.coveredCapabilityIds = Set.copyOf(coveredCapabilityIds);
            this.uncoveredCurrencies = Map.copyOf(uncoveredCurrencies);
            this.coveredCurrencies = Map.copyOf(coveredCurrencies);
        }

        /** 方案计费事件挂接了该种类。 */
        boolean relevantTo(CapabilityKind kind) {
            return kinds.contains(kind);
        }

        /** 该开通项未覆盖的币种；完整覆盖、未计算或无法归到币种时为空列表。 */
        List<String> uncoveredCurrencies(long agreementCapabilityId) {
            return uncoveredCurrencies.getOrDefault(agreementCapabilityId, List.of());
        }

        /** 该开通项已覆盖的币种；没有 ACTIVE 版本或没有覆盖时为空列表。 */
        List<String> coveredCurrencies(long agreementCapabilityId) {
            return coveredCurrencies.getOrDefault(agreementCapabilityId, List.of());
        }

        /** 有 ACTIVE 版本且完整覆盖该开通项。 */
        boolean covers(long agreementCapabilityId) {
            return activeVersion != null && coveredCapabilityIds.contains(agreementCapabilityId);
        }
    }
}
