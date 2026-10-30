package com.paytech.tax.app.ext.rest.converter;

import static com.paytech.tax.app.ext.rest.converter.TaxAmounts.amount;
import static com.paytech.tax.app.ext.rest.converter.TaxAmounts.date;
import static com.paytech.tax.app.ext.rest.converter.TaxAmounts.decimal;
import static com.paytech.tax.app.ext.rest.converter.TaxAmounts.id;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.tax.api.response.TaxDocumentResponseBody;
import com.paytech.tax.api.response.TaxOrderResponseBody;
import com.paytech.tax.api.response.TaxPolicyResponseBody;
import com.paytech.tax.api.response.TaxRegistrationResponseBody;
import com.paytech.tax.api.response.TaxReportingEntryResponseBody;
import com.paytech.tax.api.response.TaxTransactionResponseBody;
import com.paytech.tax.core.dal.dataobject.TaxDocument;
import com.paytech.tax.core.dal.dataobject.TaxMarket;
import com.paytech.tax.core.dal.dataobject.TaxPeriod;
import com.paytech.tax.core.dal.dataobject.TaxPolicy;
import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.core.dal.dataobject.TaxReportingEntry;
import com.paytech.tax.core.dal.dataobject.TaxTransaction;
import com.paytech.tax.domain.view.TaxOrderView;

import lombok.AllArgsConstructor;

/** 订单税务全貌 → 响应 Body（计算部分复用计算转换）。 */
@Component
@AllArgsConstructor
public class TaxOrderConverter {
    private final TaxCalculationConverter calculationConverter;

    /**
     * 订单视图 → 响应
     *
     * @param view 订单税务全貌
     * @return 响应
     */
    public TaxOrderResponseBody toBody(TaxOrderView view) {
        Map<Long, TaxPeriod> periods = view.getPeriods().stream()
            .collect(Collectors.toMap(TaxPeriod::getId, Function.identity()));
        TaxOrderResponseBody body = new TaxOrderResponseBody();
        body.setOrderNo(view.getOrderNo());
        body.setCalculations(view.getCalculations().stream().map(calculationConverter::toBody).toList());
        body.setTransactions(view.getTransactions().stream().map(transaction -> transaction(view, transaction, periods))
            .toList());
        Map<Long, TaxMarket> marketByRegistration = view.getMarkets().stream()
            .filter(market -> market.getRegistrationId() != null)
            .collect(Collectors.toMap(TaxMarket::getRegistrationId, Function.identity(),
                (left, right) -> left.getRevision() >= right.getRevision() ? left : right));
        body.setRegistrations(view.getRegistrations().stream()
            .map(registration -> registration(registration, marketByRegistration.get(registration.getId()))).toList());
        body.setPolicies(view.getPolicies().stream().map(TaxOrderConverter::policy).toList());
        return body;
    }

    /** 一条交易及其底稿与单据。 */
    static TaxTransactionResponseBody transaction(TaxOrderView view, TaxTransaction transaction,
        Map<Long, TaxPeriod> periods) {
        TaxTransactionResponseBody body = new TaxTransactionResponseBody();
        body.setTransactionId(id(transaction.getId()));
        body.setCalculationId(id(transaction.getCalculationId()));
        body.setSourceSystem(transaction.getSourceSystem());
        body.setSourceFactType(transaction.getSourceFactType());
        body.setSourceFactId(transaction.getSourceFactId());
        body.setTaxScopeKey(transaction.getTaxScopeKey());
        body.setOrigin(transaction.getOrigin().getCode());
        body.setTransactionKind(transaction.getTransactionKind().getCode());
        body.setTaxSubjectPartyId(id(transaction.getTaxSubjectPartyId()));
        body.setCurrency(transaction.getCurrency());
        body.setOccurredAt(transaction.getOccurredAt());
        body.setTaxPointDate(date(transaction.getTaxPointDate()));
        body.setTaxPointBasis(transaction.getTaxPointBasis());
        body.setRegisteredAt(transaction.getRegisteredAt());
        body.setReportingStatus(transaction.getReportingStatus().getCode());
        body.setReportingBlockReason(transaction.getReportingBlockReason());
        body.setReportingEntries(view.getEntries().stream()
            .filter(entry -> entry.getTransactionId().equals(transaction.getId()))
            .map(entry -> entry(entry, periods.get(entry.getPeriodId()))).toList());
        body.setDocuments(view.getDocuments().stream()
            .filter(document -> document.getTransactionId().equals(transaction.getId()))
            .map(TaxOrderConverter::document).toList());
        return body;
    }

    /** 一条底稿。 */
    static TaxReportingEntryResponseBody entry(TaxReportingEntry entry, TaxPeriod period) {
        TaxReportingEntryResponseBody body = new TaxReportingEntryResponseBody();
        body.setEntryId(id(entry.getId()));
        body.setComponentId(id(entry.getComponentId()));
        body.setRegistrationId(id(entry.getRegistrationId()));
        body.setPeriodId(id(entry.getPeriodId()));
        body.setPeriodStart(period == null ? null : date(period.getPeriodStart()));
        body.setPeriodEnd(period == null ? null : date(period.getPeriodEnd()));
        body.setFilingDueDate(period == null ? null : date(period.getFilingDueDate()));
        body.setEntryRole(entry.getEntryRole().getCode());
        body.setReportingRuleCode(entry.getReportingRuleCode());
        body.setSourceCurrency(entry.getSourceCurrency());
        body.setSourceTaxBase(amount(entry.getSourceCurrency(), entry.getSourceTaxBase()));
        body.setSourceTaxAmount(amount(entry.getSourceCurrency(), entry.getSourceTaxAmount()));
        body.setReportingCurrency(entry.getReportingCurrency());
        body.setFxRate(decimal(entry.getFxRate()));
        body.setFxRateDate(date(entry.getFxRateDate()));
        body.setReportingTaxBase(amount(entry.getReportingCurrency(), entry.getReportingTaxBase()));
        body.setReportingTaxAmount(amount(entry.getReportingCurrency(), entry.getReportingTaxAmount()));
        body.setTaxPointDate(date(entry.getTaxPointDate()));
        return body;
    }

    /** 一张单据。 */
    static TaxDocumentResponseBody document(TaxDocument document) {
        TaxDocumentResponseBody body = new TaxDocumentResponseBody();
        body.setDocumentId(id(document.getId()));
        body.setDocumentKind(document.getDocumentKind().getCode());
        body.setIssuanceMode(document.getIssuanceMode().getCode());
        body.setIssuerPartyId(id(document.getIssuerPartyId()));
        body.setRegistrationId(id(document.getRegistrationId()));
        body.setSeriesCode(document.getSeriesCode());
        body.setDocumentNumber(document.getDocumentNumber());
        body.setIssuedAt(document.getIssuedAt());
        body.setCurrency(document.getCurrency());
        body.setTemplateCode(document.getTemplateCode());
        body.setIssueStatus(document.getIssueStatus().getCode());
        body.setSubmissionStatus(document.getSubmissionStatus().getCode());
        body.setDocumentFileAddress(document.getDocumentFileAddress());
        body.setDocumentDigest(document.getDocumentDigest());
        return body;
    }

    /** 一份注册（不含受控注册号引用）；managedBy 取引用它的准入，没有时为空。 */
    static TaxRegistrationResponseBody registration(TaxRegistration registration, TaxMarket market) {
        TaxRegistrationResponseBody body = new TaxRegistrationResponseBody();
        body.setRegistrationId(id(registration.getId()));
        body.setSubjectPartyId(id(registration.getSubjectPartyId()));
        body.setJurisdictionCode(registration.getJurisdictionCode());
        body.setAuthorityCode(registration.getAuthorityCode());
        body.setTaxType(registration.getTaxType());
        body.setSchemeCode(registration.getSchemeCode());
        body.setPublicRegistrationNo(registration.getPublicRegistrationNo());
        body.setReportingCurrency(registration.getReportingCurrency());
        body.setLegalTimezone(registration.getLegalTimezone());
        body.setFilingFrequency(registration.getFilingFrequency().getCode());
        body.setStatus(registration.getStatus().getCode());
        body.setManagedBy(market == null ? null : market.getManagedBy().getCode());
        return body;
    }

    /** 一个地区包版本。 */
    static TaxPolicyResponseBody policy(TaxPolicy policy) {
        TaxPolicyResponseBody body = new TaxPolicyResponseBody();
        body.setPolicyId(id(policy.getId()));
        body.setPackageCode(policy.getPackageCode());
        body.setRevision(policy.getRevision());
        body.setJurisdictionCode(policy.getJurisdictionCode());
        body.setTaxType(policy.getTaxType());
        body.setSchemeCode(policy.getSchemeCode());
        body.setEffectiveFrom(date(policy.getEffectiveFrom()));
        body.setEffectiveTo(date(policy.getEffectiveTo()));
        body.setStatus(policy.getStatus().getCode());
        return body;
    }
}
