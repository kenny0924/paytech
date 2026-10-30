package com.paytech.tax.service.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.tax.api.enums.TaxBlockReason;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxErrorCode;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.core.constant.TaxCodes;
import com.paytech.tax.core.dal.dataobject.TaxCalculation;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;
import com.paytech.tax.core.dal.dataobject.TaxCategory;
import com.paytech.tax.core.dal.dataobject.TaxEvidence;
import com.paytech.tax.core.dal.dataobject.TaxMarket;
import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.core.enums.LiableRole;
import com.paytech.tax.core.enums.MarketCollectionStatus;
import com.paytech.tax.core.enums.TaxCategoryStatus;
import com.paytech.tax.core.enums.TaxRegistrationStatus;
import com.paytech.tax.domain.calculation.CalculateTaxContext;
import com.paytech.tax.domain.calculation.LineTaxAmounts;
import com.paytech.tax.domain.calculation.ResolvedTaxPolicy;
import com.paytech.tax.domain.calculation.TaxBlock;
import com.paytech.tax.domain.calculation.TaxCalculationView;
import com.paytech.tax.domain.policy.TaxPolicyRules;
import com.paytech.tax.domain.policy.TaxTreatmentRule;
import com.paytech.tax.domainservice.repository.TaxCategoryRepository;
import com.paytech.tax.domainservice.repository.TaxMarketRepository;
import com.paytech.tax.domainservice.repository.TaxRegistrationRepository;
import com.paytech.tax.domainservice.service.TaxCalculationService;
import com.paytech.tax.domainservice.service.TaxComputationService;
import com.paytech.tax.domainservice.service.TaxPolicyService;
import com.paytech.tax.domainservice.service.TaxQueryService;
import com.paytech.tax.service.processor.request.CalculateTaxRequest;

import lombok.AllArgsConstructor;

/**
 * 计算并保存一次税务报价（POST /tax/v1/calculations，technique 9.1）
 * <li>业务身份 sourceSystem + sourceObjectType + sourceObjectId + sourceRevision + taxScopeKey：已有计算时比较不可变请求内容，相同返回
 * 原计算，不同 409 TAX_CALCULATION_CONFLICT；并发保存撞唯一键后在事务外重读原计算并同样比较。</li>
 * <li>判定顺序：未传税务责任方或主体无任何准入 → NOT_MANAGED；缺本阶段国家证据 → MISSING_EVIDENCE；供应地无有效且 ENABLED 的准入 → MARKET_NOT_ENABLED；
 * 未知或停用税类 → UNKNOWN_TAX_CATEGORY；NO_COLLECTION_REQUIRED → 组件 NOT_COLLECTED、税 0；REGISTRATION_REQUIRED 需有效注册与
 * 可用地区包（否则 MARKET_NOT_ENABLED / POLICY_INVALID），逐行恰好命中一条规则（否则 NO_RULE / AMBIGUOUS_RULE）后计算。</li>
 * <li>只读本库主数据，不调用外部服务；一个本地事务写头、行、组件与证据。不加资源锁：同一身份的并发由唯一键兜底。</li>
 */
@Component
@AllArgsConstructor
public class CalculateTaxProcessor extends AbstractProcessor<CalculateTaxRequest, TaxCalculationView> {
    /** 可能出现在税类目录中的代码：可打印 ASCII。 */
    static final Pattern CATALOG_CODE = Pattern.compile("[\\x21-\\x7E]{1,64}");

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TaxQueryService taxQueryService;
    private final TaxPolicyService taxPolicyService;
    private final TaxMarketRepository taxMarketRepository;
    private final TaxCalculationService taxCalculationService;
    private final TaxCategoryRepository taxCategoryRepository;
    private final TaxComputationService taxComputationService;
    private final TaxRegistrationRepository taxRegistrationRepository;

    /** 行号、行引用与证据类型唯一；金额符合币种精度；行金额等于数量 × 单价 − 折扣。 */
    @Override
    protected void validate(CalculateTaxRequest request) {
        requireUnique(request.getLines().stream().map(CalculateTaxRequest.Line::getLineNo).toList(), "lines.lineNo");
        requireUnique(request.getLines().stream().map(CalculateTaxRequest.Line::getLineRef).toList(), "lines.lineRef");
        requireUnique(request.getEvidence().stream().map(CalculateTaxRequest.Evidence::getType).toList(), "evidence.type");
        if (request.getDeclaredTaxAmount() != null) {
            Money.of(request.getCurrency(), request.getDeclaredTaxAmount());
        }
        for (CalculateTaxRequest.Line line : request.getLines()) {
            checkLineAmounts(request.getCurrency(), line);
        }
    }

    @Override
    protected TaxCalculationView processRequest(CalculateTaxRequest request) {
        // 查询原计算
        TaxCalculationView original = getOriginalCalculation(request);
        if (original != null) {
            return original;
        }

        // 构建上下文
        CalculateTaxContext context = buildContext(request);

        // 保存上下文
        try {
            taxCalculationService.saveCalculation(context);
        } catch (DuplicateKeyException raced) {
            return getRacedCalculation(request, raced);
        }

        // 响应处理
        return context.toView();
    }

    /** 同一身份已有计算时比较内容：相同返回原计算，不同 409；没有返回 null。 */
    TaxCalculationView getOriginalCalculation(CalculateTaxRequest request) {
        TaxCalculationView original = taxQueryService.findCalculation(request.getSourceSystem(),
            request.getSourceObjectType(), request.getSourceObjectId(), request.getSourceRevision(),
            request.getTaxScopeKey());
        if (original == null) {
            return null;
        }
        if (!sameRequest(original, request)) {
            throw new FailException(TaxErrorCode.TAX_CALCULATION_CONFLICT, "同一计算身份已以不同内容计算过: "
                + request.getSourceObjectType() + " " + request.getSourceObjectId() + ", 版本 "
                + request.getSourceRevision() + ", 原计算 " + original.getCalculation().getId());
        }
        return original;
    }

    /** 并发保存撞唯一键：事务已回滚，重读先提交的计算并比较。 */
    TaxCalculationView getRacedCalculation(CalculateTaxRequest request, DuplicateKeyException raced) {
        TaxCalculationView original = getOriginalCalculation(request);
        if (original == null) {
            throw new ErrorException("税务计算唯一键冲突但未读到原计算: " + request.getSourceObjectId(), raced);
        }
        return original;
    }

    /** 按 technique 9.1 的判定顺序构造本次计算。 */
    CalculateTaxContext buildContext(CalculateTaxRequest request) {
        CalculationDraft draft = new CalculationDraft(request, idGenerator::nextId,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (request.getTaxSubjectPartyId() == null) {
            return draft.notManaged();
        }
        List<TaxMarket> markets = taxMarketRepository.findBySubject(request.getTaxSubjectPartyId());
        if (markets.isEmpty()) {
            return draft.notManaged();
        }
        TaxEvidenceType basis = TaxCalculationView.basisEvidenceType(request.getStage());
        String country = request.countryOf(basis);
        if (country == null) {
            return draft.blocked(List.of(new TaxBlock(TaxBlockReason.MISSING_EVIDENCE, "evidence." + basis.getCode())));
        }
        TaxMarket market = selectMarket(markets, country, request.getTaxPointDate());
        if (market == null || market.getCollectionStatus() != MarketCollectionStatus.ENABLED) {
            return draft.blocked(List.of(new TaxBlock(TaxBlockReason.MARKET_NOT_ENABLED, "market." + country)));
        }
        List<TaxBlock> unknownCategories = unknownCategories(request);
        if (!unknownCategories.isEmpty()) {
            return draft.blocked(unknownCategories);
        }
        return switch (market.getObligation()) {
            case NO_COLLECTION_REQUIRED -> notCollected(draft, request, market, country);
            case REGISTRATION_REQUIRED -> computed(draft, request, market, country);
            default -> draft.blocked(List.of(new TaxBlock(TaxBlockReason.MARKET_NOT_ENABLED, "market." + country)));
        };
    }

    /**
     * 供应地在税点日期有效的已批准准入：每个评估取最大修订；多于一个评估同时有效视为未启用（准入冲突需运营处理）
     *
     * @return 唯一有效准入；没有或冲突返回 null
     */
    TaxMarket selectMarket(List<TaxMarket> markets, String country, LocalDate date) {
        Map<String, TaxMarket> latest = markets.stream()
            .filter(market -> market.getJurisdictionCode().equals(country) && market.getApprovedAt() != null)
            .filter(market -> !date.isBefore(market.getValidFrom())
                && (market.getValidTo() == null || date.isBefore(market.getValidTo())))
            .collect(Collectors.toMap(TaxMarket::getAssessmentNo, market -> market,
                (left, right) -> left.getRevision() >= right.getRevision() ? left : right));
        return latest.size() == 1 ? latest.values().iterator().next() : null;
    }

    /**
     * 不在税类目录或已停用的行；路径中的序号为行号 lineNo
     * <li>税类是 Client 自由文本：只把可能是目录代码的值（可打印 ASCII）拿去查询，其余直接视为未知，避免与 ascii_bin 目录列比较。</li>
     */
    List<TaxBlock> unknownCategories(CalculateTaxRequest request) {
        Set<String> active = taxCategoryRepository.findByCodes(request.getLines().stream()
            .map(CalculateTaxRequest.Line::getCategoryCode).filter(code -> CATALOG_CODE.matcher(code).matches())
            .collect(Collectors.toSet())).stream()
            .filter(category -> category.getStatus() == TaxCategoryStatus.ACTIVE).map(TaxCategory::getCategoryCode)
            .collect(Collectors.toSet());
        List<TaxBlock> blocks = new ArrayList<>();
        for (int index = 0; index < request.getLines().size(); index++) {
            if (!active.contains(request.getLines().get(index).getCategoryCode())) {
                blocks.add(new TaxBlock(TaxBlockReason.UNKNOWN_TAX_CATEGORY,
                    "lines[" + request.getLines().get(index).getLineNo() + "].categoryCode"));
            }
        }
        return blocks;
    }

    /** 获准不收：每行一个 NOT_COLLECTED 组件、税 0；原因码取地区包（可空）或缺省。 */
    CalculateTaxContext notCollected(CalculationDraft draft, CalculateTaxRequest request, TaxMarket market,
        String country) {
        ResolvedTaxPolicy policy = taxPolicyService.resolveForCalculation(market.getPolicyId(), country,
            request.getTaxPointDate());
        String reason = policy == null || policy.rules().getNotCollectedReasonCode() == null
            ? TaxCodes.DEFAULT_NOT_COLLECTED_REASON : policy.rules().getNotCollectedReasonCode();
        int scale = minorUnit(request.getCurrency());
        List<LineTaxAmounts> amounts = new ArrayList<>();
        List<TaxCalculationComponent> components = new ArrayList<>();
        for (CalculateTaxRequest.Line line : request.getLines()) {
            LineTaxAmounts amount = taxComputationService.computeLine(request.getPriceMode(), line.getLineAmount(), null,
                RoundingMode.HALF_UP, scale);
            amounts.add(amount);
            components.add(component(market, policy, country, null, TaxTreatment.NOT_COLLECTED, reason, amount,
                RoundingMode.HALF_UP, scale));
        }
        int validity = policy == null ? TaxPolicyRules.DEFAULT_QUOTE_VALIDITY_HOURS : policy.rules().getQuoteValidityHours();
        return draft.ready(amounts, components, TaxCodes.TAX_POINT_PAYMENT, validity);
    }

    /** 按地区包计税：需有效注册与可用地区包，逐行恰好命中一条规则。 */
    CalculateTaxContext computed(CalculationDraft draft, CalculateTaxRequest request, TaxMarket market,
        String country) {
        if (usableRegistration(market, request.getTaxPointDate()) == null) {
            return draft.blocked(List.of(new TaxBlock(TaxBlockReason.MARKET_NOT_ENABLED,
                "market." + country + ".registration")));
        }
        ResolvedTaxPolicy policy = taxPolicyService.resolveForCalculation(market.getPolicyId(), country,
            request.getTaxPointDate());
        Integer scale = policy == null ? null : roundingScale(policy.rules(), request.getCurrency());
        if (scale == null) {
            return draft.blocked(List.of(new TaxBlock(TaxBlockReason.POLICY_INVALID, "market." + country + ".policy")));
        }
        List<TaxTreatmentRule> rules = new ArrayList<>();
        List<TaxBlock> blocks = matchRules(policy.rules(), request, country, rules);
        if (!blocks.isEmpty()) {
            return draft.blocked(blocks);
        }
        return computeLines(draft, request, market, policy, country, rules, scale);
    }

    /** 逐行计算金额并生成组件。 */
    CalculateTaxContext computeLines(CalculationDraft draft, CalculateTaxRequest request, TaxMarket market,
        ResolvedTaxPolicy policy, String country, List<TaxTreatmentRule> rules, int scale) {
        RoundingMode rounding = policy.rules().getRoundingMode();
        List<LineTaxAmounts> amounts = new ArrayList<>();
        List<TaxCalculationComponent> components = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            TaxTreatmentRule rule = rules.get(index);
            LineTaxAmounts amount = taxComputationService.computeLine(request.getPriceMode(),
                request.getLines().get(index).getLineAmount(), rule.getRate(), rounding, scale);
            amounts.add(amount);
            components.add(component(market, policy, country, rule, rule.getTreatment(), rule.getReasonCode(), amount,
                rounding, scale));
        }
        return draft.ready(amounts, components, policy.rules().getTaxPointBasis(), policy.rules().getQuoteValidityHours());
    }

    /** 每行按事实匹配规则；0 条 NO_RULE，多于 1 条 AMBIGUOUS_RULE。命中的规则按行序写入 matched。 */
    List<TaxBlock> matchRules(TaxPolicyRules rules, CalculateTaxRequest request, String country,
        List<TaxTreatmentRule> matched) {
        List<TaxBlock> blocks = new ArrayList<>();
        for (int index = 0; index < request.getLines().size(); index++) {
            Map<String, String> facts = Map.of("supplyKind", request.getSupplyKind(), "categoryCode",
                request.getLines().get(index).getCategoryCode(), "placeOfSupply", country, "buyerTaxpayerStatus",
                TaxCodes.BUYER_NON_REGISTERED);
            List<TaxTreatmentRule> hits = rules.getSupplyKinds().contains(request.getSupplyKind()) ? rules.match(facts)
                : List.of();
            if (hits.size() == 1) {
                matched.add(hits.get(0));
                continue;
            }
            blocks.add(new TaxBlock(hits.isEmpty() ? TaxBlockReason.NO_RULE : TaxBlockReason.AMBIGUOUS_RULE,
                "lines[" + request.getLines().get(index).getLineNo() + "]"));
        }
        return blocks;
    }

    /** 构造一个组件（ID 与行在 draft 中绑定）。 */
    TaxCalculationComponent component(TaxMarket market, ResolvedTaxPolicy policy, String country,
        TaxTreatmentRule rule, TaxTreatment treatment, String reason, LineTaxAmounts amount, RoundingMode rounding,
        int scale) {
        TaxCalculationComponent component = new TaxCalculationComponent();
        component.setComponentCode(rule == null ? market.getTaxType() : rule.getComponentCode());
        component.setJurisdictionCode(country);
        component.setTaxType(policy == null ? market.getTaxType() : policy.policy().getTaxType());
        component.setTreatment(treatment);
        component.setReasonCode(reason);
        component.setLiableRole(rule == null ? LiableRole.SELLER : rule.getLiableRole());
        component.setMarketId(market.getId());
        component.setRegistrationId(market.getRegistrationId());
        component.setPolicyId(policy == null ? null : policy.policy().getId());
        component.setRuleCode(rule == null ? null : rule.getRuleCode());
        component.setTaxBase(amount.net());
        component.setRate(rule == null ? null : rule.getRate());
        component.setChargedTaxAmount(amount.tax());
        component.setCalculationMethod(amount.method());
        component.setRoundingMethod(rounding.name());
        component.setRoundingScale(scale);
        return component;
    }

    /** 准入指向的注册：同主体、ACTIVE、税点日期有效；否则 null。 */
    TaxRegistration usableRegistration(TaxMarket market, LocalDate date) {
        TaxRegistration registration = market.getRegistrationId() == null ? null
            : taxRegistrationRepository.findById(market.getRegistrationId());
        boolean usable = registration != null && registration.getStatus() == TaxRegistrationStatus.ACTIVE
            && Objects.equals(registration.getSubjectPartyId(), market.getSubjectPartyId())
            && !date.isBefore(registration.getEffectiveFrom())
            && (registration.getEffectiveTo() == null || date.isBefore(registration.getEffectiveTo()));
        return usable ? registration : null;
    }

    /** 取整位数：地区包给出时不得超过币种 minor unit；超过返回 null（POLICY_INVALID）。 */
    static Integer roundingScale(TaxPolicyRules rules, String currency) {
        int minor = minorUnit(currency);
        if (rules.getRoundingScale() == null) {
            return minor;
        }
        return rules.getRoundingScale() <= minor ? rules.getRoundingScale() : null;
    }

    /** 币种 minor unit。 */
    static int minorUnit(String currency) {
        return Currency.getInstance(currency).getDefaultFractionDigits();
    }

    /** 金额精度与行金额等式。 */
    static void checkLineAmounts(String currency, CalculateTaxRequest.Line line) {
        Money.of(currency, line.getUnitPrice());
        Money.of(currency, line.getDiscountAmount());
        Money.of(currency, line.getLineAmount());
        if (line.getQuantity().signum() <= 0) {
            throw new InvalidParameterException("行数量必须大于 0: 行 " + line.getLineNo());
        }
        BigDecimal merchandise = line.getQuantity().multiply(line.getUnitPrice());
        if (line.getDiscountAmount().compareTo(merchandise) > 0
            || merchandise.subtract(line.getDiscountAmount()).compareTo(line.getLineAmount()) != 0) {
            throw new InvalidParameterException("行金额必须等于数量 × 单价 − 折扣且折扣不大于数量 × 单价: 行 " + line.getLineNo());
        }
    }

    /** 列表元素唯一。 */
    static void requireUnique(List<?> values, String field) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new InvalidParameterException("请求中 " + field + " 重复");
        }
    }

    /** 原计算与本次请求的不可变内容一致：头、证据（NOT_MANAGED 不保存证据，不比较）与行；自报税额只对 NOT_MANAGED 比较。 */
    static boolean sameRequest(TaxCalculationView original, CalculateTaxRequest request) {
        TaxCalculation calculation = original.getCalculation();
        boolean header = calculation.getSourceObjectNo().equals(request.getSourceObjectNo())
            && calculation.getCalculationStage() == request.getStage()
            && calculation.getSupplyKind().equals(request.getSupplyKind())
            && calculation.getBusinessMode().equals(request.getBusinessMode())
            && Objects.equals(calculation.getSellerPartyId(), request.getSellerPartyId())
            && Objects.equals(calculation.getTaxSubjectPartyId(), request.getTaxSubjectPartyId())
            && calculation.getBuyerClientId() == request.getBuyerClientId()
            && calculation.getAcquiringEntityNo().equals(request.getEntityNo())
            && calculation.getCurrency().equals(request.getCurrency())
            && calculation.getPriceMode() == request.getPriceMode()
            && calculation.getTaxPointDate().equals(request.getTaxPointDate());
        boolean evidence = calculation.getStatus() == TaxCalculationStatus.NOT_MANAGED
            || sameEvidence(original.getEvidence(), request);
        return header && sameDeclared(calculation, request) && evidence && sameLines(original.getLines(), request);
    }

    /** NOT_MANAGED 的税额等于本次自报值（缺省 0）。 */
    static boolean sameDeclared(TaxCalculation calculation, CalculateTaxRequest request) {
        if (calculation.getStatus() != TaxCalculationStatus.NOT_MANAGED) {
            return true;
        }
        BigDecimal declared = request.getDeclaredTaxAmount() == null ? BigDecimal.ZERO : request.getDeclaredTaxAmount();
        return calculation.getChargedTaxAmount().compareTo(declared) == 0;
    }

    /** 证据集合一致（类型、国家、行政区）。 */
    static boolean sameEvidence(List<TaxEvidence> evidence, CalculateTaxRequest request) {
        Set<List<Object>> stored = evidence.stream().map(item -> List.<Object>of(item.getEvidenceType(),
            item.getCountryCode(), Objects.toString(item.getSubdivisionCode(), ""))).collect(Collectors.toSet());
        Set<List<Object>> requested = request.getEvidence().stream().map(item -> List.<Object>of(item.getType(),
            item.getCountryCode(), Objects.toString(item.getSubdivisionCode(), ""))).collect(Collectors.toSet());
        return stored.equals(requested);
    }

    /** 行逐条一致（行号、引用、税类、数量、单价、折扣）；行金额由三者决定。 */
    static boolean sameLines(List<TaxCalculationLine> lines, CalculateTaxRequest request) {
        if (lines.size() != request.getLines().size()) {
            return false;
        }
        Map<Integer, CalculateTaxRequest.Line> byNo = request.getLines().stream()
            .collect(Collectors.toMap(CalculateTaxRequest.Line::getLineNo, line -> line));
        return lines.stream().allMatch(line -> {
            CalculateTaxRequest.Line requested = byNo.get(line.getLineNo());
            return requested != null && requested.getLineRef().equals(line.getSourceLineRef())
                && Objects.equals(requested.getDescription(), line.getDescription())
                && requested.getCategoryCode().equals(line.getCategoryCode())
                && requested.getQuantity().compareTo(line.getQuantity()) == 0
                && requested.getUnitPrice().compareTo(line.getUnitPrice()) == 0
                && requested.getDiscountAmount().compareTo(line.getDiscountAmount()) == 0;
        });
    }
}
