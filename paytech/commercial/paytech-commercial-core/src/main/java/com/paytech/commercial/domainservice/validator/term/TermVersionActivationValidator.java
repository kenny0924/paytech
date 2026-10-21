package com.paytech.commercial.domainservice.validator.term;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.domain.CapabilityAcceptance;
import com.paytech.commercial.domain.CommercialPriceCandidate;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.PriceRelevanceIssue;
import com.paytech.commercial.domain.PricingFacts;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.CommercialTermComponentBandMode;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 激活校验（technique.md §2.6、开通与定价模型调整 §4.6–§4.7），排期与激活都执行，失败抛出 BizCheckFailException（400），消息以"激活校验 N"开头
 * <li>1 版本状态（排期：合同方案 CLIENT_SIGNED 或免签 APPROVED，模板 APPROVED；激活 SCHEDULED）；合同 ACTIVE 且区间覆盖生效起点。</li>
 * <li>2 每条价格引用的画像 ACTIVE、OE 被合同覆盖；Client 合同方案还要求零开通与相关性（TermPriceRelevance），Partner 方案要求币种在该 OE
 * 开通项的 CURRENCY 条件内。</li>
 * <li>3 完整性：合同下与本方案计费事件种类相关的每个 ENABLED 开通项（功能项除外）× CURRENCY 每个值 × 其余条件维度取值组合，至少命中一条价格；
 * 组合数上限 1000。</li>
 * <li>4 组件：币种等于价格币种；TIERED 档位从 0 开始、连续、无重叠、末档上界为空；本期只接受 BLENDED、TRANSACTION_AMOUNT，拒绝
 * RETROACTIVE。</li>
 * <li>5 同方案区间不重叠：生效起点严格晚于已生效 / 被替代版本的起点且不早于其终点；已有其他 SCHEDULED 版本（窗口未封闭）一律拒绝。</li>
 * <li>标准价目模板只做 1（状态）、画像 ACTIVE、4 与 5；纯校验，不访问数据库，输入由 Processor 在锁内一次读取。</li>
 */
public final class TermVersionActivationValidator {
    /** 参与区间重叠判断的状态。 */
    static final Set<CommercialTermVersionStatus> EFFECTIVE_STATUSES = Set.of(CommercialTermVersionStatus.SCHEDULED,
        CommercialTermVersionStatus.ACTIVE, CommercialTermVersionStatus.SUPERSEDED);

    /** 完整性检查单个开通项的条件组合上限。 */
    static final int MAX_COMBINATIONS = 1000;

    /** 纯静态校验。 */
    private TermVersionActivationValidator() {
    }

    /**
     * 排期校验：签署状态满足要求，生效起点不早于当前时间，再执行 1–5
     *
     * @param context 含版本、价格集合、合同、画像、开通项与同方案版本
     * @param effectiveFrom 拟写入的生效起点
     */
    public static void checkSchedule(CommercialTermVersionContext context, Instant effectiveFrom) {
        checkSignature(context);
        if (effectiveFrom.isBefore(context.getNow())) {
            throw new BizCheckFailException("激活校验 1（合同与状态）：生效起点不能早于当前时间: " + effectiveFrom);
        }
        checkAll(context, effectiveFrom);
    }

    /**
     * 激活校验：SCHEDULED 且生效起点已到，再执行 1–5
     *
     * @param context 含版本、价格集合、合同、画像、开通项与同方案版本
     */
    public static void checkActivate(CommercialTermVersionContext context) {
        CommercialTermVersion version = context.getVersion();
        checkStatus(version, CommercialTermVersionStatus.SCHEDULED);
        if (version.getEffectiveFrom().isAfter(context.getNow())) {
            throw new BizCheckFailException("激活校验 1（合同与状态）：生效起点未到: " + version.getEffectiveFrom());
        }
        checkAll(context, version.getEffectiveFrom());
    }

    /**
     * 第 1–5 条；第 6 条毛利校验（design.md §12）本期不做。
     * <li>TODO by CLAUDE: 第 6 条毛利校验待定价 · 成本 · 路由毛利快照立项后在此接入（technique.md §2.6 第 6 条留接口位）。</li>
     */
    static void checkAll(CommercialTermVersionContext context, Instant effectiveFrom) {
        if (context.template()) {
            checkProfilesActive(context);
        } else {
            checkAgreement(context.getAgreement(), effectiveFrom);
            checkPriceReferences(context);
            checkCoverage(context);
        }
        checkComponents(context);
        checkNoOverlap(context, effectiveFrom);
    }

    /** 1：排期前的签署状态；模板与免签版本 APPROVED 即可，合同方案须 CLIENT_SIGNED。 */
    static void checkSignature(CommercialTermVersionContext context) {
        CommercialTermVersion version = context.getVersion();
        boolean signatureFree = context.template() || Boolean.TRUE.equals(version.getSignatureExempt());
        if (!signatureFree && version.getStatus() == CommercialTermVersionStatus.APPROVED) {
            throw new BizCheckFailException("激活校验 1（合同与状态）：价格协议尚未签署: " + version.getTermVersionId());
        }
        checkStatus(version, signatureFree ? CommercialTermVersionStatus.APPROVED : CommercialTermVersionStatus.CLIENT_SIGNED);
    }

    /** 1：版本状态。 */
    static void checkStatus(CommercialTermVersion version, CommercialTermVersionStatus expected) {
        if (version.getStatus() != expected) {
            throw new BizCheckFailException("激活校验 1（合同与状态）：版本状态须为 " + expected.getCode() + "，实际 "
                + version.getStatus().getCode() + ": " + version.getTermVersionId());
        }
    }

    /** 1：合同 ACTIVE 且 effectiveFrom ∈ [合同起点, 合同终点)。 */
    static void checkAgreement(Agreement agreement, Instant effectiveFrom) {
        boolean covered = !effectiveFrom.isBefore(agreement.getEffectiveFrom())
            && (agreement.getEffectiveTo() == null || effectiveFrom.isBefore(agreement.getEffectiveTo()));
        if (agreement.getStatus() != AgreementStatus.ACTIVE || !covered) {
            throw new BizCheckFailException("激活校验 1（合同与状态）：合同须 ACTIVE 且区间覆盖生效起点: 合同 "
                + agreement.getAgreementId() + ", 状态 " + agreement.getStatus().getCode() + ", 生效起点 " + effectiveFrom);
        }
    }

    /** 2（模板）：每条价格引用的画像 ACTIVE。 */
    static void checkProfilesActive(CommercialTermVersionContext context) {
        for (CommercialTermPrice price : context.getPrices()) {
            PricingProfile profile = profile(context, price.getPricingProfileCode());
            if (profile == null || profile.getStatus() != PricingProfileStatus.ACTIVE) {
                throw new BizCheckFailException("激活校验 2（价格引用）：画像不是 ACTIVE: " + label(price));
            }
        }
    }

    /** 2：画像 ACTIVE、OE 覆盖；Client 合同方案零开通与相关性，Partner 方案币种在开通项条件内。 */
    static void checkPriceReferences(CommercialTermVersionContext context) {
        checkProfilesActive(context);
        for (CommercialTermPrice price : context.getPrices()) {
            if (!context.covers(price.getEntityNo())) {
                throw new BizCheckFailException("激活校验 2（价格引用）：合同不覆盖 OE: " + label(price));
            }
            if (!context.clientContract() && !currencyAllowed(context, price)) {
                throw new BizCheckFailException("激活校验 2（价格引用）：币种不在该 OE 开通项的币种条件内: " + label(price));
            }
        }
        if (context.clientContract()) {
            List<PriceRelevanceIssue> issues = new TermPriceRelevance(acceptances(context), context.getFeeEvents())
                .issues(candidates(context), context.getComponents());
            if (!issues.isEmpty()) {
                PriceRelevanceIssue issue = issues.getFirst();
                throw new BizCheckFailException("激活校验 2（价格引用）：价格与开通项不相关（" + issue.getReason().getCode() + "）: "
                    + issue.label() + "，" + issue.getMessage());
            }
        }
    }

    /** Partner：该 OE 没有相关开通项或存在不限币种的开通项时不限制；否则币种须在条件并集内。 */
    static boolean currencyAllowed(CommercialTermVersionContext context, CommercialTermPrice price) {
        List<CapabilityAcceptance> onEntity = relevantCapabilities(context).stream()
            .filter(capability -> capability.on(price.getEntityNo())).toList();
        return onEntity.isEmpty() || onEntity.stream()
            .anyMatch(capability -> capability.conditions().allows(PricingDimension.CURRENCY, price.getCurrency()));
    }

    /** 3：每个 ENABLED 相关开通项（功能项除外）× CURRENCY 每个值 × 其余条件组合至少命中一条价格。 */
    static void checkCoverage(CommercialTermVersionContext context) {
        if (context.getPrices().isEmpty()) {
            throw new BizCheckFailException("激活校验 3（覆盖完整）：版本没有价格: " + context.getVersion().getTermVersionId());
        }
        List<CommercialPriceCandidate> candidates = candidates(context);
        for (CapabilityAcceptance capability : relevantCapabilities(context)) {
            if (!capability.enabled() || capability.kind() == CapabilityKind.FEATURE || !context.covers(
                capability.capability().getEntityNo())) {
                continue;
            }
            checkCapabilityCoverage(candidates, capability);
        }
    }

    /** 3：单个开通项的条件空间；没有 CURRENCY 条件时价格中出现的任一币种命中即可。 */
    static void checkCapabilityCoverage(List<CommercialPriceCandidate> candidates, CapabilityAcceptance capability) {
        Set<String> currencies = capability.conditions().values(PricingDimension.CURRENCY);
        List<Map<PricingDimension, String>> combinations = capability.conditions().combinations(MAX_COMBINATIONS);
        if (combinations == null || (long) Math.max(currencies.size(), 1) * combinations.size() > MAX_COMBINATIONS) {
            throw new BizCheckFailException("激活校验 3（覆盖完整）：开通项条件组合超过 " + MAX_COMBINATIONS + "，请收窄接受条件: "
                + capabilityLabel(capability));
        }
        if (currencies.isEmpty()) {
            boolean anyCurrency = candidates.stream().map(candidate -> candidate.price().getCurrency()).distinct()
                .anyMatch(currency -> combinations.stream().allMatch(combination -> hits(candidates, capability, currency,
                    combination)));
            if (!anyCurrency) {
                throw coverageGap(capability, "任一币种", Map.of());
            }
            return;
        }
        for (String currency : currencies) {
            for (Map<PricingDimension, String> combination : combinations) {
                if (!hits(candidates, capability, currency, combination)) {
                    throw coverageGap(capability, currency, combination);
                }
            }
        }
    }

    /** 某开通项在某币种与条件组合下是否命中一条价格：同 OE、同币种、支付方式精确、价格条件全部由已知事实满足。 */
    static boolean hits(List<CommercialPriceCandidate> candidates, CapabilityAcceptance capability, String currency,
        Map<PricingDimension, String> combination) {
        String paymentMethodCode = capability.catalog().getPaymentMethodCode();
        PricingFacts facts = new PricingFacts(paymentMethodCode, currency, combination);
        return candidates.stream().anyMatch(candidate -> candidate.price().getEntityNo().equals(capability.capability().getEntityNo())
            && candidate.price().getCurrency().equals(currency) && candidate.appliesTo(paymentMethodCode)
            && candidate.matches(facts));
    }

    /** 覆盖缺口。 */
    static BizCheckFailException coverageGap(CapabilityAcceptance capability, String currency,
        Map<PricingDimension, String> combination) {
        String detail = combination.entrySet().stream().map(entry -> entry.getKey().getCode() + "=" + entry.getValue())
            .collect(Collectors.joining(","));
        return new BizCheckFailException("激活校验 3（覆盖完整）：开通项没有命中的价格: " + capabilityLabel(capability) + "/"
            + currency + (detail.isEmpty() ? "" : "/" + detail));
    }

    /** 开通项定位。 */
    static String capabilityLabel(CapabilityAcceptance capability) {
        return capability.capability().getEntityNo() + "/" + capability.capability().getCapabilityCode();
    }

    /** 4：本期定价模型、组件币种、阶梯基数与模式、档位连续。 */
    static void checkComponents(CommercialTermVersionContext context) {
        if (context.getVersion().getPricingModel() != PricingModel.BLENDED) {
            throw new BizCheckFailException("激活校验 4（组件与档位）：本期定价模型只接受 BLENDED: "
                + context.getVersion().getPricingModel().getCode());
        }
        for (CommercialTermPrice price : context.getPrices()) {
            for (CommercialTermComponent component : context.getComponents()) {
                if (component.getTermPriceId().equals(price.getTermPriceId())) {
                    checkComponent(context, price, component);
                }
            }
        }
    }

    /** 4：单个组件。 */
    static void checkComponent(CommercialTermVersionContext context, CommercialTermPrice price,
        CommercialTermComponent component) {
        String label = label(price) + "/" + component.getComponentCode();
        if (component.getCurrency() != null && !component.getCurrency().equals(price.getCurrency())) {
            throw new BizCheckFailException("激活校验 4（组件与档位）：组件币种与价格币种不一致: " + label);
        }
        if (component.getComponentType() != CommercialTermComponentComponentType.TIERED) {
            return;
        }
        if (component.getBandBasis() != BandBasis.TRANSACTION_AMOUNT) {
            throw new BizCheckFailException("激活校验 4（组件与档位）：本期阶梯基数只接受 TRANSACTION_AMOUNT: " + label);
        }
        if (component.getBandMode() == CommercialTermComponentBandMode.RETROACTIVE) {
            throw new BizCheckFailException("激活校验 4（组件与档位）：本期不接受 RETROACTIVE: " + label);
        }
        List<CommercialTermComponentBand> bands = context.getBands().stream()
            .filter(band -> band.getTermComponentId().equals(component.getTermComponentId()))
            .sorted(Comparator.comparing(CommercialTermComponentBand::getBandNo)).toList();
        checkBands(bands, label);
    }

    /** 4：档位从 0 开始、上界等于下一档下界且大于本档下界、末档上界为空。 */
    static void checkBands(List<CommercialTermComponentBand> bands, String label) {
        if (bands.isEmpty() || bands.getFirst().getLowerBound().compareTo(BigDecimal.ZERO) != 0) {
            throw new BizCheckFailException("激活校验 4（组件与档位）：首档下界必须为 0: " + label);
        }
        for (int index = 0; index < bands.size(); index++) {
            CommercialTermComponentBand band = bands.get(index);
            boolean last = index == bands.size() - 1;
            if (last && band.getUpperBound() != null) {
                throw new BizCheckFailException("激活校验 4（组件与档位）：末档上界必须为空: " + label);
            }
            if (!last && (band.getUpperBound() == null || band.getUpperBound().compareTo(band.getLowerBound()) <= 0
                || band.getUpperBound().compareTo(bands.get(index + 1).getLowerBound()) != 0)) {
                throw new BizCheckFailException("激活校验 4（组件与档位）：档位不连续或重叠: " + label + ", 第 "
                    + band.getBandNo() + " 档");
            }
        }
    }

    /** 5：其他 SCHEDULED 版本一律重叠；生效起点严格晚于其他 ACTIVE / SUPERSEDED 版本的起点，且不早于其终点。 */
    static void checkNoOverlap(CommercialTermVersionContext context, Instant effectiveFrom) {
        Long self = context.getVersion().getTermVersionId();
        for (CommercialTermVersion other : context.getPlanVersions()) {
            if (other.getTermVersionId().equals(self) || !EFFECTIVE_STATUSES.contains(other.getStatus())) {
                continue;
            }
            boolean overlaps = other.getStatus() == CommercialTermVersionStatus.SCHEDULED
                || !effectiveFrom.isAfter(other.getEffectiveFrom())
                || (other.getEffectiveTo() != null && effectiveFrom.isBefore(other.getEffectiveTo()));
            if (overlaps) {
                throw new BizCheckFailException("激活校验 5（区间不重叠）：与第 " + other.getVersionNo() + " 版（"
                    + other.getStatus().getCode() + "，起点 " + other.getEffectiveFrom() + "）区间重叠: " + effectiveFrom);
            }
        }
    }

    /** 合同开通项与条件、目录的组合。 */
    static List<CapabilityAcceptance> acceptances(CommercialTermVersionContext context) {
        return CapabilityAcceptance.of(context.getCapabilities(), context.getCapabilityLimits(),
            context.getProductCapabilities());
    }

    /** 与本方案计费事件挂接种类相关的开通项。 */
    static List<CapabilityAcceptance> relevantCapabilities(CommercialTermVersionContext context) {
        Set<CapabilityKind> kinds = context.getFeeEvents().stream().map(ProductFeeEvent::getCapabilityKind)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        return acceptances(context).stream().filter(capability -> kinds.contains(capability.kind())).toList();
    }

    /** 版本价格的命中候选；画像缺失的价格已在第 2 条拒绝。 */
    static List<CommercialPriceCandidate> candidates(CommercialTermVersionContext context) {
        return context.getPrices().stream().map(price -> {
            PricingProfile profile = profile(context, price.getPricingProfileCode());
            return new CommercialPriceCandidate(price, profile, context.getProfileConditions().stream()
                .filter(condition -> condition.getPricingProfileId().equals(profile.getPricingProfileId())).toList());
        }).toList();
    }

    /** 按代码取画像。 */
    static PricingProfile profile(CommercialTermVersionContext context, String code) {
        return context.getProfiles().stream().filter(profile -> profile.getPricingProfileCode().equals(code)).findFirst()
            .orElse(null);
    }

    /** 价格定位。 */
    static String label(CommercialTermPrice price) {
        return price.getEntityNo() + "/" + price.getCurrency() + "/" + price.getPricingProfileCode();
    }
}
