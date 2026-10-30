package com.paytech.operations.console.bff.service.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.domain.TaxOrderPageContext;
import com.paytech.operations.console.bff.domain.enums.TaxCalloutKind;
import com.paytech.operations.console.bff.domain.enums.TaxProgressState;
import com.paytech.operations.console.bff.domain.enums.TaxProgressStep;
import com.paytech.operations.console.bff.integration.TaxClient;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Amounts;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.BlockReason;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Callout;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Line;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Matching;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Quote;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Step;
import com.paytech.operations.console.bff.service.processor.request.GetOrderTaxRequest;
import com.paytech.tax.api.enums.TaxBlockReason;
import com.paytech.tax.api.response.TaxCalculationLineResponseBody;
import com.paytech.tax.api.response.TaxCalculationResponseBody;
import com.paytech.tax.api.response.TaxComponentResponseBody;

import lombok.AllArgsConstructor;

/**
 * 税务查询页（AC-13）：单次读取 tax 订单税务全貌，组装标题状态、进度、提示卡、金额、逐行明细、报价记录与阻断原因；右栏由
 * {@link TaxOrderPageSections} 组装
 * <li>tax 是计算、正式交易、底稿与单据的唯一 Owner；BFF 只做展示推导，不改写金额、不补算税额，也不缓存。</li>
 * <li>tax 404 TAX_ORDER_NOT_FOUND 原样到达页面（"订单未找到"空状态）；tax 响应缺失必需部分按 502 失败。</li>
 * <li>不由平台计税的订单 tax 不记录支付结果，支付步骤标为 UNTRACKED，不伪造"支付成功"。</li>
 * <li>tax 返回后再由 {@link TaxOrderPartyNames} 补 Client 与 Operating Entity 名称；名称读取失败只降级为显示 ID。</li>
 */
@Component
@AllArgsConstructor
public class GetOrderTaxProcessor extends AbstractProcessor<GetOrderTaxRequest, TaxOrderPageResponseBody> {
    /** 规则包未命中的阻断原因。 */
    static final Set<String> POLICY_MISS = Set.of(TaxBlockReason.MARKET_NOT_ENABLED.getCode(),
        TaxBlockReason.NO_RULE.getCode(), TaxBlockReason.AMBIGUOUS_RULE.getCode(), TaxBlockReason.POLICY_INVALID.getCode());

    /** 指向行的阻断路径，例如 lines[2].categoryCode；方括号内为行号 lineNo（从 1 开始，tax-api 变更 2 第 6 条）。 */
    static final Pattern LINE_PATH = Pattern.compile("^lines\\[(\\d{1,3})].*");

    private final TaxClient tax;
    private final TaxOrderPartyNames partyNames;

    @Override
    protected TaxOrderPageResponseBody processRequest(GetOrderTaxRequest request) {
        var context = new TaxOrderPageContext(tax.orderTax(request.getOrderNo()));
        var current = context.getCurrent();
        var page = new TaxOrderPageResponseBody();
        page.setOrderNo(request.getOrderNo());
        page.setPageStatus(context.pageStatus().getCode());
        page.setBusinessMode(current.getBusinessMode());
        page.setBuyerClientId(current.getBuyerClientId());
        page.setEntityNo(current.getEntityNo());
        page.setTaxSubjectPartyId(current.getTaxSubjectPartyId());
        page.setCurrency(current.getCurrency());
        page.setManagedBy(managedBy(context));
        page.setSteps(steps(context));
        page.setCallout(callout(context));
        page.setAmounts(amounts(current));
        page.setLines(lines(current));
        page.setQuotes(quotes(context));
        page.setBlockReasons(blockReasons(current));
        page.setMatching(context.blocked() ? matching(current) : null);
        TaxOrderPageSections.fill(page, context);
        partyNames.fill(page, request.getOperator());
        return page;
    }

    /** 当前（已登记时为登记依据）报价引用的税务注册的 ManagedBy；没有为 null。 */
    static String managedBy(TaxOrderPageContext context) {
        var calc = context.registered() ? context.registeredCalculation() : context.getCurrent();
        var registration = context.registrationOf(calc);
        return registration == null ? null : registration.getManagedBy();
    }

    /** 进度：MoR 五步；不由平台计税三步（建单自报 → 支付不由 tax 记录 → 平台登记不适用）。 */
    List<Step> steps(TaxOrderPageContext context) {
        if (context.notManaged()) {
            var quote = calculationStep(TaxProgressStep.QUOTE, TaxProgressState.DONE, context.getCurrent());
            return List.of(quote, step(TaxProgressStep.PAYMENT, TaxProgressState.UNTRACKED),
                step(TaxProgressStep.REGISTRATION, TaxProgressState.NOT_APPLICABLE));
        }
        var first = context.first();
        var firstState = first == context.getCurrent() && context.blocked() ? TaxProgressState.BLOCKED
            : TaxProgressState.DONE;
        return List.of(calculationStep(TaxProgressStep.QUOTE, firstState, first), recalculationStep(context),
            paymentStep(context), registrationStep(context), reportingStep(context));
    }

    /** 资料提交重算：没有最终报价为 PENDING；最终报价被阻断为 BLOCKED。 */
    Step recalculationStep(TaxOrderPageContext context) {
        var latestFinal = context.latestFinal();
        if (latestFinal == null) {
            return step(TaxProgressStep.RECALCULATION, TaxProgressState.PENDING);
        }
        var state = latestFinal == context.getCurrent() && context.blocked() ? TaxProgressState.BLOCKED
            : TaxProgressState.DONE;
        return calculationStep(TaxProgressStep.RECALCULATION, state, latestFinal);
    }

    /** 支付：以 tax 正式交易为准，金额为交易依据报价的应付总额。 */
    Step paymentStep(TaxOrderPageContext context) {
        if (!context.registered()) {
            return step(TaxProgressStep.PAYMENT, TaxProgressState.PENDING);
        }
        var payment = step(TaxProgressStep.PAYMENT, TaxProgressState.DONE);
        payment.setAmount(context.registeredCalculation().getGrossAmount());
        payment.setOccurredAt(context.getTransaction().getOccurredAt());
        return payment;
    }

    /** 登记与开票：税票开具为 DONE；已登记未开票为 ATTENTION。 */
    Step registrationStep(TaxOrderPageContext context) {
        if (!context.registered()) {
            return step(TaxProgressStep.REGISTRATION, TaxProgressState.PENDING);
        }
        if (!context.issued()) {
            var registration = step(TaxProgressStep.REGISTRATION, TaxProgressState.ATTENTION);
            registration.setOccurredAt(context.getTransaction().getRegisteredAt());
            return registration;
        }
        var registration = step(TaxProgressStep.REGISTRATION, TaxProgressState.DONE);
        registration.setDocumentNumber(context.getDocument().getDocumentNumber());
        registration.setOccurredAt(context.getDocument().getIssuedAt());
        return registration;
    }

    /** 申报底稿：READY 为 DONE；PENDING（缺法定汇率）为 ATTENTION 并带申报币种。 */
    Step reportingStep(TaxOrderPageContext context) {
        if (!context.registered()) {
            return step(TaxProgressStep.REPORTING, TaxProgressState.PENDING);
        }
        if (!context.reportingPending()) {
            return step(TaxProgressStep.REPORTING, TaxProgressState.DONE);
        }
        var reporting = step(TaxProgressStep.REPORTING, TaxProgressState.ATTENTION);
        var registration = context.registrationOf(context.registeredCalculation());
        reporting.setReportingCurrency(registration == null ? null : registration.getReportingCurrency());
        return reporting;
    }

    /** 报价步骤：税额、作为依据的证据国家与计算时间。 */
    static Step calculationStep(TaxProgressStep key, TaxProgressState state, TaxCalculationResponseBody calc) {
        var step = step(key, state);
        step.setAmount(calc.getTaxAmount());
        var evidence = TaxOrderPageContext.placeOfSupply(calc);
        step.setCountryCode(evidence == null ? null : evidence.getCountryCode());
        step.setOccurredAt(calc.getCalculatedAt());
        return step;
    }

    /** 只有键和状态的步骤。 */
    static Step step(TaxProgressStep key, TaxProgressState state) {
        var step = new Step();
        step.setKey(key.getCode());
        step.setState(state.getCode());
        return step;
    }

    /**
     * 提示卡：不由平台计税 → 阻断 → 已登记（缺汇率 / 未开票 / 完成）→ 暂估 → 待支付，取第一项
     * <li>缺汇率提示带订单币种税额与申报币种：只影响申报，不影响收款与入账。</li>
     */
    Callout callout(TaxOrderPageContext context) {
        if (context.notManaged()) {
            return callout(TaxCalloutKind.CLIENT_RESPONSIBLE);
        }
        if (context.registered()) {
            return registeredCallout(context);
        }
        if (context.blocked()) {
            return callout(TaxCalloutKind.PAYMENT_BLOCKED);
        }
        if (context.latestFinal() == null) {
            return callout(TaxCalloutKind.ESTIMATE_ONLY);
        }
        var awaiting = callout(TaxCalloutKind.AWAITING_PAYMENT);
        awaiting.setExpiresAt(context.getCurrent().getExpiresAt());
        return awaiting;
    }

    /** 已登记订单的提示：缺汇率优先，其次未开票，否则完成。 */
    Callout registeredCallout(TaxOrderPageContext context) {
        if (context.reportingPending()) {
            var callout = callout(TaxCalloutKind.REPORTING_FX_MISSING);
            callout.setTaxAmount(context.registeredCalculation().getTaxAmount());
            var registration = context.registrationOf(context.registeredCalculation());
            callout.setReportingCurrency(registration == null ? null : registration.getReportingCurrency());
            return callout;
        }
        return callout(context.issued() ? TaxCalloutKind.REGISTERED : TaxCalloutKind.DOCUMENT_PENDING);
    }

    /** 只有类型与色调的提示。 */
    static Callout callout(TaxCalloutKind kind) {
        var callout = new Callout();
        callout.setKind(kind.getCode());
        callout.setTone(kind.getTone());
        return callout;
    }

    /** 当前报价金额。 */
    static Amounts amounts(TaxCalculationResponseBody calc) {
        var amounts = new Amounts();
        amounts.setCalculationId(calc.getCalculationId());
        amounts.setRevision(calc.getSourceRevision());
        amounts.setStage(calc.getStage());
        amounts.setStatus(calc.getStatus());
        amounts.setTaxAmountSource(calc.getTaxAmountSource());
        amounts.setNetAmount(calc.getNetAmount());
        amounts.setTaxAmount(calc.getTaxAmount());
        amounts.setGrossAmount(calc.getGrossAmount());
        amounts.setExpiresAt(calc.getExpiresAt());
        return amounts;
    }

    /** 逐行明细按行号升序；主组件取行内第一个组件。 */
    static List<Line> lines(TaxCalculationResponseBody calc) {
        if (calc.getLines() == null) {
            return List.of();
        }
        return calc.getLines().stream().sorted(Comparator.comparing(TaxCalculationLineResponseBody::getLineNo))
            .map(GetOrderTaxProcessor::line).toList();
    }

    /** 一行明细。 */
    static Line line(TaxCalculationLineResponseBody source) {
        var line = new Line();
        line.setLineNo(source.getLineNo());
        line.setLineRef(source.getLineRef());
        line.setCategoryCode(source.getCategoryCode());
        line.setDescription(source.getDescription());
        line.setQuantity(source.getQuantity());
        line.setNetAmount(source.getNetAmount());
        line.setTaxAmount(source.getTaxAmount());
        List<TaxComponentResponseBody> components = source.getComponents() == null ? List.of() : source.getComponents();
        line.setComponentCount(components.size());
        if (!components.isEmpty()) {
            line.setRate(components.getFirst().getRate());
            line.setTreatment(components.getFirst().getTreatment());
            line.setRuleCode(components.getFirst().getRuleCode());
        }
        return line;
    }

    /** 报价记录按 revision 倒序；每条与上一次比较证据国家与税额。 */
    static List<Quote> quotes(TaxOrderPageContext context) {
        var quotes = new ArrayList<Quote>();
        TaxCalculationResponseBody previous = null;
        for (var calc : context.getCalculations()) {
            quotes.add(quote(calc, previous, calc == context.getCurrent()));
            previous = calc;
        }
        return quotes.reversed();
    }

    /** 一次报价及相对上一次的变化。 */
    static Quote quote(TaxCalculationResponseBody calc, TaxCalculationResponseBody previous, boolean current) {
        var quote = new Quote();
        quote.setCalculationId(calc.getCalculationId());
        quote.setRevision(calc.getSourceRevision());
        quote.setStage(calc.getStage());
        quote.setStatus(calc.getStatus());
        quote.setCurrent(current);
        quote.setTaxAmount(calc.getTaxAmount());
        quote.setCalculatedAt(calc.getCalculatedAt());
        var evidence = TaxOrderPageContext.placeOfSupply(calc);
        quote.setEvidenceType(evidence == null ? null : evidence.getType());
        quote.setCountryCode(evidence == null ? null : evidence.getCountryCode());
        if (previous != null) {
            var before = TaxOrderPageContext.placeOfSupply(previous);
            String beforeCountry = before == null ? null : before.getCountryCode();
            quote.setPreviousCountryCode(Objects.equals(beforeCountry, quote.getCountryCode()) ? null : beforeCountry);
            quote.setTaxDelta(delta(previous.getTaxAmount(), calc.getTaxAmount()));
        }
        return quote;
    }

    /** 税额变化（after − before，保持币种精度）；任一缺失为 null。 */
    static String delta(String before, String after) {
        if (before == null || after == null) {
            return null;
        }
        return new BigDecimal(after).subtract(new BigDecimal(before)).toPlainString();
    }

    /** 当前报价的阻断原因，行路径解析为行号与品类。 */
    static List<BlockReason> blockReasons(TaxCalculationResponseBody calc) {
        if (calc.getBlockReasons() == null) {
            return List.of();
        }
        var evidence = TaxOrderPageContext.placeOfSupply(calc);
        return calc.getBlockReasons().stream().map(source -> {
            var reason = new BlockReason();
            reason.setCode(source.getCode());
            reason.setPath(source.getPath());
            reason.setCountryCode(evidence == null ? null : evidence.getCountryCode());
            var line = pathLine(calc, source.getPath());
            reason.setLineNo(line == null ? null : line.getLineNo());
            reason.setCategoryCode(line == null ? null : line.getCategoryCode());
            return reason;
        }).toList();
    }

    /** 路径 lines[n] 指向的行（按行号）；不指向行或行不存在为 null。 */
    static TaxCalculationLineResponseBody pathLine(TaxCalculationResponseBody calc, String path) {
        var matcher = path == null ? null : LINE_PATH.matcher(path);
        if (matcher == null || !matcher.matches() || calc.getLines() == null) {
            return null;
        }
        int lineNo = Integer.parseInt(matcher.group(1));
        return calc.getLines().stream().filter(line -> Objects.equals(line.getLineNo(), lineNo)).findFirst()
            .orElse(null);
    }

    /** 被阻断报价的匹配结果：证据、市场准入与规则包是否命中。 */
    static Matching matching(TaxCalculationResponseBody calc) {
        var matching = new Matching();
        var evidence = TaxOrderPageContext.placeOfSupply(calc);
        matching.setEvidenceType(evidence == null ? null : evidence.getType());
        matching.setCountryCode(evidence == null ? null : evidence.getCountryCode());
        var codes = calc.getBlockReasons() == null ? List.<String>of()
            : calc.getBlockReasons().stream().map(reason -> reason.getCode()).toList();
        matching.setMarketMatched(!codes.contains(TaxBlockReason.MARKET_NOT_ENABLED.getCode()));
        matching.setPolicyMatched(codes.stream().noneMatch(POLICY_MISS::contains));
        return matching;
    }
}
