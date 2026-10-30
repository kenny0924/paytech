package com.paytech.tax.service.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxReportingStatus;
import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.core.constant.TaxCodes;
import com.paytech.tax.core.dal.dataobject.TaxCalculation;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.dataobject.TaxPeriod;
import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.core.dal.dataobject.TaxReportingEntry;
import com.paytech.tax.core.dal.dataobject.TaxTransaction;
import com.paytech.tax.core.enums.ReportingEntryRole;
import com.paytech.tax.core.enums.TaxTransactionKind;
import com.paytech.tax.core.enums.TaxTransactionOrigin;
import com.paytech.tax.domain.calculation.ResolvedTaxPolicy;
import com.paytech.tax.domain.calculation.TaxCalculationView;
import com.paytech.tax.domain.registration.OrderPaymentTaxFact;
import com.paytech.tax.domain.registration.RegisterTaxTransactionContext;
import com.paytech.tax.domainservice.repository.TaxRegistrationRepository;
import com.paytech.tax.domainservice.repository.TaxTransactionRepository;
import com.paytech.tax.domainservice.service.TaxPeriodService;
import com.paytech.tax.domainservice.service.TaxPolicyService;
import com.paytech.tax.domainservice.service.TaxQueryService;
import com.paytech.tax.domainservice.service.TaxTransactionService;
import com.paytech.tax.integration.fx.TaxFxRateClient;
import com.paytech.tax.service.handler.TaxDocumentIssueHandler;

import lombok.AllArgsConstructor;

/**
 * 按 acquiring OrderPaymentSucceeded 登记正式税务交易与申报底稿（technique 9.2）
 * <li>只处理带 READY 计算的事件（存量订单与 NOT_MANAGED 由 Handler 跳过）。业务唯一身份
 * acquiring / ORDER_PAYMENT_SUCCEEDED / 订单 ID / 税务范围：已登记且来源载荷 Hash 相同返回原交易，不同为业务拒绝（进死信）。</li>
 * <li>冻结计算须为 READY、FINAL，币种、订单与税务范围一致，税额合计等于事件 taxAmount 与 taxComponents 合计；否则业务拒绝（B5）。</li>
 * <li>事务外确定期间（必要时生成）与法定汇率（同币种为 1；跨币种取不到时不建该底稿、交易 PENDING(FX_RATE_MISSING)）；随后一个本地事务
 * 写交易、底稿与 TAX_DOCUMENT_ISSUE 开票任务（地区包有 documents.receipt 时）。</li>
 * <li>不加资源锁：同一订单的并发登记由源身份唯一键兜底，冲突回滚后重读原交易并比较 Hash。</li>
 */
@Component
@AllArgsConstructor
public class RegisterTaxTransactionProcessor extends AbstractProcessor<OrderPaymentTaxFact, TaxTransaction> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TaxFxRateClient taxFxRateClient;
    private final TaxQueryService taxQueryService;
    private final TaxPeriodService taxPeriodService;
    private final TaxPolicyService taxPolicyService;
    private final TaxTransactionService taxTransactionService;
    private final TaxTransactionRepository taxTransactionRepository;
    private final TaxRegistrationRepository taxRegistrationRepository;

    /** 只接受带计算 ID 的 READY 事件。 */
    @Override
    protected void validate(OrderPaymentTaxFact fact) {
        if (fact.getTaxCalculationId() == null || !TaxCalculationStatus.READY.getCode().equals(fact.getTaxStatus())) {
            throw new BizCheckFailException("订单支付成功事件没有 READY 税务计算: 订单 " + fact.getOrderId());
        }
    }

    @Override
    protected TaxTransaction processRequest(OrderPaymentTaxFact fact) {
        // 查询原登记
        TaxTransaction original = getOriginalTransaction(fact);
        if (original != null) {
            return original;
        }

        // 读取并核对冻结计算
        TaxCalculationView calculation = getAndCheckCalculation(fact);

        // 构建上下文（事务外确定期间与汇率）
        RegisterTaxTransactionContext context = buildContext(fact, calculation);

        // 保存上下文
        try {
            taxTransactionService.saveTransaction(context);
        } catch (DuplicateKeyException raced) {
            return getRacedTransaction(fact, raced);
        }

        // 响应处理
        return context.getTransaction();
    }

    /** 同一源事实已登记：Hash 相同返回原交易，不同业务拒绝；未登记返回 null。 */
    TaxTransaction getOriginalTransaction(OrderPaymentTaxFact fact) {
        TaxTransaction original = taxTransactionRepository.findBySource(TaxCodes.SOURCE_SYSTEM_ACQUIRING,
            TaxCodes.FACT_ORDER_PAYMENT_SUCCEEDED, Long.toString(fact.getOrderId()), fact.getTaxScopeKey());
        if (original == null) {
            return null;
        }
        if (!original.getSourcePayloadHash().equals(fact.getPayloadHash())) {
            throw new BizCheckFailException("订单已登记税务交易且事件内容不同: 订单 " + fact.getOrderId() + ", 原交易 "
                + original.getId() + ", 事件 " + fact.getEventId());
        }
        return original;
    }

    /** 并发登记撞唯一键：事务已回滚，重读原交易并比较。 */
    TaxTransaction getRacedTransaction(OrderPaymentTaxFact fact, DuplicateKeyException raced) {
        TaxTransaction original = getOriginalTransaction(fact);
        if (original == null) {
            throw new ErrorException("税务交易唯一键冲突但未读到原交易: 订单 " + fact.getOrderId(), raced);
        }
        return original;
    }

    /** 冻结计算存在且与事件一致；不一致为业务拒绝（死信）。 */
    TaxCalculationView getAndCheckCalculation(OrderPaymentTaxFact fact) {
        TaxCalculationView view = taxQueryService.findCalculation(fact.getTaxCalculationId());
        String context = ": 订单 " + fact.getOrderId() + ", 计算 " + fact.getTaxCalculationId();
        if (view == null) {
            throw new BizCheckFailException("事件引用的税务计算不存在" + context);
        }
        TaxCalculation calculation = view.getCalculation();
        boolean consistent = calculation.getStatus() == TaxCalculationStatus.READY
            && calculation.getCalculationStage() == TaxCalculationStage.FINAL
            && calculation.getSourceObjectId() == fact.getOrderId()
            && calculation.getTaxScopeKey().equals(fact.getTaxScopeKey())
            && calculation.getCurrency().equals(fact.getCurrency());
        if (!consistent) {
            throw new BizCheckFailException("事件与冻结计算的状态、阶段、订单、税务范围或币种不一致" + context);
        }
        BigDecimal charged = calculation.getChargedTaxAmount();
        if ((fact.getTaxAmount() != null && fact.getTaxAmount().compareTo(charged) != 0)
            || fact.componentTotal().compareTo(charged) != 0) {
            throw new BizCheckFailException("事件税额与冻结计算不一致" + context);
        }
        return view;
    }

    /** 交易、逐组件 OUTPUT 底稿与开票任务。 */
    RegisterTaxTransactionContext buildContext(OrderPaymentTaxFact fact, TaxCalculationView view) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<TaxCalculationComponent> taxable = view.getComponents().stream()
            .filter(component -> component.getTreatment() == TaxTreatment.TAXABLE
                && component.getChargedTaxAmount().signum() > 0 && component.getRegistrationId() != null)
            .toList();
        Map<Long, TaxRegistration> registrations = taxRegistrationRepository.findByIds(
            taxable.stream().map(TaxCalculationComponent::getRegistrationId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(TaxRegistration::getId, Function.identity()));
        TaxTransaction transaction = newTransaction(fact, view.getCalculation(), registrations, taxable, now);
        List<TaxReportingEntry> entries = new ArrayList<>();
        for (TaxCalculationComponent component : taxable) {
            TaxReportingEntry entry = newEntry(fact, transaction, component,
                requireRegistration(registrations, component), now);
            if (entry == null) {
                transaction.setReportingStatus(TaxReportingStatus.PENDING);
                transaction.setReportingBlockReason(TaxCodes.REPORTING_FX_RATE_MISSING);
                continue;
            }
            entries.add(entry);
        }
        return new RegisterTaxTransactionContext(transaction, List.copyOf(entries), documentIssueEvent(transaction, taxable));
    }

    /** 新交易：税点日期取首个应税组件注册的法定时区，没有应税组件按 UTC。 */
    TaxTransaction newTransaction(OrderPaymentTaxFact fact, TaxCalculation calculation,
        Map<Long, TaxRegistration> registrations, List<TaxCalculationComponent> taxable, Instant now) {
        TaxTransaction transaction = new TaxTransaction();
        transaction.setId(idGenerator.nextId());
        transaction.setCalculationId(calculation.getId());
        transaction.setSourceSystem(TaxCodes.SOURCE_SYSTEM_ACQUIRING);
        transaction.setSourceFactType(TaxCodes.FACT_ORDER_PAYMENT_SUCCEEDED);
        transaction.setSourceFactId(Long.toString(fact.getOrderId()));
        transaction.setTaxScopeKey(fact.getTaxScopeKey());
        transaction.setOrigin(TaxTransactionOrigin.AUTO);
        transaction.setTransactionKind(TaxTransactionKind.ORIGINAL);
        transaction.setTaxSubjectPartyId(calculation.getTaxSubjectPartyId());
        transaction.setCurrency(calculation.getCurrency());
        transaction.setOccurredAt(fact.getPaymentSucceededTime());
        transaction.setTaxPointDate(taxable.isEmpty() ? localDate(fact.getPaymentSucceededTime(), ZoneOffset.UTC)
            : localDate(fact.getPaymentSucceededTime(), requireRegistration(registrations, taxable.get(0))));
        transaction.setTaxPointBasis(calculation.getTaxPointBasis());
        transaction.setRegisteredAt(now);
        transaction.setReportingStatus(TaxReportingStatus.READY);
        transaction.setSourcePayloadHash(fact.getPayloadHash());
        transaction.setCreateTime(now);
        transaction.setUpdateTime(now);
        return transaction;
    }

    /**
     * 一个应税组件的 OUTPUT 底稿：期间按注册法定时区的税点日期确定（必要时生成），同币种汇率为 1
     *
     * @return 底稿；跨币种且没有法定汇率时返回 null
     */
    TaxReportingEntry newEntry(OrderPaymentTaxFact fact, TaxTransaction transaction, TaxCalculationComponent component,
        TaxRegistration registration, Instant now) {
        LocalDate taxPointDate = localDate(fact.getPaymentSucceededTime(), registration);
        ResolvedTaxPolicy policy = taxPolicyService.loadReferenced(component.getPolicyId());
        TaxPeriod period = taxPeriodService.ensurePeriod(registration, policy.rules(), taxPointDate);
        boolean sameCurrency = transaction.getCurrency().equals(registration.getReportingCurrency());
        Optional<BigDecimal> rate = sameCurrency ? Optional.of(BigDecimal.ONE)
            : taxFxRateClient.findStatutoryRate(transaction.getCurrency(), registration.getReportingCurrency(), taxPointDate);
        if (rate.isEmpty()) {
            return null;
        }
        int reportingScale = Currency.getInstance(registration.getReportingCurrency()).getDefaultFractionDigits();
        TaxReportingEntry entry = new TaxReportingEntry();
        entry.setId(idGenerator.nextId());
        entry.setTransactionId(transaction.getId());
        entry.setComponentId(component.getId());
        entry.setRegistrationId(registration.getId());
        entry.setPeriodId(period.getId());
        entry.setEntryRole(ReportingEntryRole.OUTPUT);
        entry.setReportingRuleCode(policy.rules().getReportingRuleCode());
        entry.setReportingPolicyId(policy.policy().getId());
        entry.setSourceCurrency(transaction.getCurrency());
        entry.setSourceTaxBase(component.getTaxBase());
        entry.setSourceTaxAmount(component.getChargedTaxAmount());
        entry.setReportingCurrency(registration.getReportingCurrency());
        entry.setFxRate(rate.get());
        entry.setFxRateDate(taxPointDate);
        entry.setFxSourceRef(sameCurrency ? TaxCodes.FX_SAME_CURRENCY : TaxCodes.FX_STATUTORY);
        entry.setReportingTaxBase(component.getTaxBase().multiply(rate.get()).setScale(reportingScale, RoundingMode.HALF_UP));
        entry.setReportingTaxAmount(
            component.getChargedTaxAmount().multiply(rate.get()).setScale(reportingScale, RoundingMode.HALF_UP));
        entry.setTaxPointDate(taxPointDate);
        entry.setCreateTime(now);
        return entry;
    }

    /** 首个应税组件的地区包要求开票时登记 TAX_DOCUMENT_ISSUE；否则不开票。 */
    CompensationEventRequest documentIssueEvent(TaxTransaction transaction, List<TaxCalculationComponent> taxable) {
        if (taxable.isEmpty() || taxPolicyService.loadReferenced(taxable.get(0).getPolicyId()).rules().getReceipt() == null) {
            return null;
        }
        return TaxDocumentIssueHandler.buildEvent(transaction.getId());
    }

    /** 组件引用的注册必须存在（计算时已校验）。 */
    static TaxRegistration requireRegistration(Map<Long, TaxRegistration> registrations,
        TaxCalculationComponent component) {
        TaxRegistration registration = registrations.get(component.getRegistrationId());
        if (registration == null) {
            throw new ErrorException("计算组件引用的注册不存在: " + component.getRegistrationId());
        }
        return registration;
    }

    /** 时刻在注册法定时区的本地日期。 */
    static LocalDate localDate(Instant instant, TaxRegistration registration) {
        return localDate(instant, ZoneId.of(registration.getLegalTimezone()));
    }

    /** 时刻在给定时区的本地日期。 */
    static LocalDate localDate(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate();
    }
}
