package com.paytech.operations.console.bff.service.processor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.paytech.operations.console.bff.domain.TaxOrderPageContext;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Basis;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Explanation;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.NotManaged;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Receipt;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Registration;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody.Reporting;
import com.paytech.tax.api.enums.TaxAmountSource;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.response.TaxComponentResponseBody;
import com.paytech.tax.api.response.TaxReportingEntryResponseBody;

/**
 * 税务查询页右栏与计税说明的纯组装：税票、正式登记、申报底稿、计算依据、不由平台计税的说明，以及左栏的一句话计税说明输入
 * <li>只读取 {@link TaxOrderPageContext} 中已加载的 tax 事实，不查询、不调用外部服务。</li>
 * <li>只供 {@link GetOrderTaxProcessor} 使用；拆出只为控制 Processor 篇幅。</li>
 */
final class TaxOrderPageSections {

    /** 工具类不实例化。 */
    private TaxOrderPageSections() {
    }

    /** 填充右栏各段与计税说明。 */
    static void fill(TaxOrderPageResponseBody page, TaxOrderPageContext context) {
        page.setExplanation(explanation(context));
        page.setReceipt(receipt(context));
        page.setRegistration(registration(context));
        page.setReporting(reporting(context));
        page.setBasis(basis(context));
        page.setNotManaged(context.notManaged() ? notManaged(context) : null);
    }

    /**
     * 一句话计税说明的输入：当前报价 READY 且税额由 tax 计算时返回
     * <li>行内组件按（辖区、税种、处理、税率）去重；多于一种时只标 mixed，由页面给出汇总说明。</li>
     */
    static Explanation explanation(TaxOrderPageContext context) {
        var current = context.getCurrent();
        if (!context.ready() || !TaxAmountSource.COMPUTED.getCode().equals(current.getTaxAmountSource())) {
            return null;
        }
        List<TaxComponentResponseBody> components = TaxOrderPageContext.components(current);
        if (components.isEmpty()) {
            return null;
        }
        var explanation = new Explanation();
        long kinds = components.stream().map(item -> List.of(Objects.toString(item.getJurisdictionCode()),
            Objects.toString(item.getTaxType()), Objects.toString(item.getTreatment()), Objects.toString(item.getRate())))
            .distinct().count();
        if (kinds > 1) {
            explanation.setMixed(true);
            return explanation;
        }
        describeSingle(explanation, components.getFirst(), context);
        return explanation;
    }

    /** 单一组件的说明：辖区、税种、处理、税率、供应地证据、注册方案与地区包。 */
    static void describeSingle(Explanation explanation, TaxComponentResponseBody component, TaxOrderPageContext context) {
        explanation.setJurisdictionCode(component.getJurisdictionCode());
        explanation.setTaxType(component.getTaxType());
        explanation.setTreatment(component.getTreatment());
        explanation.setRate(component.getRate());
        var evidence = TaxOrderPageContext.placeOfSupply(context.getCurrent());
        explanation.setEvidenceType(evidence == null ? null : evidence.getType());
        explanation.setCountryCode(evidence == null ? null : evidence.getCountryCode());
        var registration = context.registration(component.getRegistrationId());
        explanation.setSchemeCode(registration == null ? null : registration.getSchemeCode());
        var policy = context.policy(component.getPolicyId());
        explanation.setPackageCode(policy == null ? null : policy.getPackageCode());
        explanation.setPolicyRevision(policy == null ? null : policy.getRevision());
    }

    /** 税票：ISSUED 且有文件地址才可下载；没有单据为 null。 */
    static Receipt receipt(TaxOrderPageContext context) {
        var document = context.getDocument();
        if (document == null) {
            return null;
        }
        var receipt = new Receipt();
        receipt.setDocumentId(document.getDocumentId());
        receipt.setDocumentKind(document.getDocumentKind());
        receipt.setDocumentNumber(document.getDocumentNumber());
        receipt.setIssueStatus(document.getIssueStatus());
        receipt.setIssuedAt(document.getIssuedAt());
        receipt.setDownloadable(TaxDocumentIssueStatus.ISSUED.getCode().equals(document.getIssueStatus())
            && document.getDocumentFileAddress() != null);
        return receipt;
    }

    /** 正式登记：交易时间、税点、法定时区与来源事实；未登记为 null。 */
    static Registration registration(TaxOrderPageContext context) {
        var transaction = context.getTransaction();
        if (transaction == null) {
            return null;
        }
        var registration = new Registration();
        registration.setTransactionId(transaction.getTransactionId());
        registration.setRegisteredAt(transaction.getRegisteredAt());
        registration.setTaxPointDate(transaction.getTaxPointDate());
        var taxRegistration = context.registrationOf(context.registeredCalculation());
        registration.setLegalTimezone(taxRegistration == null ? null : taxRegistration.getLegalTimezone());
        registration.setSourceFactType(transaction.getSourceFactType());
        registration.setSourceFactId(transaction.getSourceFactId());
        registration.setCalculationRevision(context.registeredCalculation().getSourceRevision());
        return registration;
    }

    /** 申报底稿：PENDING 只有订单币种税额与申报币种；READY 取首条底稿的期间、截止日与申报币种合计。 */
    static Reporting reporting(TaxOrderPageContext context) {
        var transaction = context.getTransaction();
        if (transaction == null) {
            return null;
        }
        var reporting = new Reporting();
        reporting.setStatus(transaction.getReportingStatus());
        reporting.setBlockReason(transaction.getReportingBlockReason());
        reporting.setTaxAmount(context.registeredCalculation().getTaxAmount());
        var taxRegistration = context.registrationOf(context.registeredCalculation());
        reporting.setFilingFrequency(taxRegistration == null ? null : taxRegistration.getFilingFrequency());
        reporting.setReportingCurrency(taxRegistration == null ? null : taxRegistration.getReportingCurrency());
        List<TaxReportingEntryResponseBody> entries = transaction.getReportingEntries() == null ? List.of()
            : transaction.getReportingEntries();
        if (!entries.isEmpty()) {
            var entry = entries.getFirst();
            reporting.setPeriodStart(entry.getPeriodStart());
            reporting.setPeriodEnd(entry.getPeriodEnd());
            reporting.setFilingDueDate(entry.getFilingDueDate());
            reporting.setReportingCurrency(entry.getReportingCurrency());
            reporting.setReportingTaxAmount(sum(entries));
        }
        return reporting;
    }

    /** 各底稿行申报币种税额之和；任一缺失为 null。 */
    static String sum(List<TaxReportingEntryResponseBody> entries) {
        if (entries.stream().anyMatch(entry -> entry.getReportingTaxAmount() == null)) {
            return null;
        }
        return entries.stream().map(entry -> new BigDecimal(entry.getReportingTaxAmount()))
            .reduce(BigDecimal::add).orElseThrow().toPlainString();
    }

    /** 计算依据：当前报价首个组件的地区包、注册与取整；没有组件为 null。 */
    static Basis basis(TaxOrderPageContext context) {
        var components = TaxOrderPageContext.components(context.getCurrent());
        if (components.isEmpty()) {
            return null;
        }
        var component = components.getFirst();
        var basis = new Basis();
        var policy = context.policy(component.getPolicyId());
        basis.setPackageCode(policy == null ? null : policy.getPackageCode());
        basis.setPolicyRevision(policy == null ? null : policy.getRevision());
        var registration = context.registration(component.getRegistrationId());
        basis.setJurisdictionCode(registration == null ? component.getJurisdictionCode()
            : registration.getJurisdictionCode());
        basis.setSchemeCode(registration == null ? null : registration.getSchemeCode());
        basis.setMarketEnabled(component.getMarketId() != null);
        basis.setRoundingMethod(component.getRoundingMethod());
        basis.setRoundingScale(component.getRoundingScale());
        return basis;
    }

    /** 不由平台计税的说明输入。 */
    static NotManaged notManaged(TaxOrderPageContext context) {
        var current = context.getCurrent();
        var notManaged = new NotManaged();
        notManaged.setReason(current.getNotManagedReason());
        notManaged.setCalculationId(current.getCalculationId());
        notManaged.setBusinessMode(current.getBusinessMode());
        notManaged.setTaxSubjectPartyId(current.getTaxSubjectPartyId());
        notManaged.setBuyerClientId(current.getBuyerClientId());
        return notManaged;
    }
}
