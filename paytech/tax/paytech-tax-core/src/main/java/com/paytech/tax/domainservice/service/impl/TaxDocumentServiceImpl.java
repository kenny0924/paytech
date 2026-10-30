package com.paytech.tax.domainservice.service.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.tax.api.enums.IssuanceMode;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.enums.TaxDocumentKind;
import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.core.constant.TaxCodes;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;
import com.paytech.tax.core.dal.dataobject.TaxCategory;
import com.paytech.tax.core.dal.dataobject.TaxDocument;
import com.paytech.tax.core.dal.dataobject.TaxDocumentSequence;
import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.core.dal.dataobject.TaxTransaction;
import com.paytech.tax.core.enums.DocumentSubmissionStatus;
import com.paytech.tax.domain.calculation.ResolvedTaxPolicy;
import com.paytech.tax.domain.calculation.TaxCalculationView;
import com.paytech.tax.domain.document.TaxDocumentIssueContext;
import com.paytech.tax.domain.document.TaxReceiptContent;
import com.paytech.tax.domain.policy.TaxReceiptRule;
import com.paytech.tax.domainservice.repository.TaxCalculationComponentRepository;
import com.paytech.tax.domainservice.repository.TaxCalculationLineRepository;
import com.paytech.tax.domainservice.repository.TaxCalculationRepository;
import com.paytech.tax.domainservice.repository.TaxCategoryRepository;
import com.paytech.tax.domainservice.repository.TaxDocumentRepository;
import com.paytech.tax.domainservice.repository.TaxDocumentSequenceRepository;
import com.paytech.tax.domainservice.repository.TaxRegistrationRepository;
import com.paytech.tax.domainservice.repository.TaxTransactionRepository;
import com.paytech.tax.domainservice.service.TaxDocumentService;
import com.paytech.tax.domainservice.service.TaxPolicyService;

import lombok.AllArgsConstructor;

/**
 * TaxDocumentService 实现
 * <li>号码：锁定 (issuer, registration, series) 序列行后以 CAS 递增，同一短事务插入 RESERVED 单据；不同交易并发开票在序列行上串行，
 * 不会撞号。同一交易并发分号由 uk_tax_document 拦截，回滚后不占号。失败或重试不回收、不重新取号。</li>
 * <li>series_code = 地区包 seriesPrefix + "-" + 分号时间在注册法定时区的年份；年度序列首次使用时自动建立。</li>
 */
@Service
@AllArgsConstructor
public class TaxDocumentServiceImpl implements TaxDocumentService {
    private final IdGenerator idGenerator;
    private final TaxPolicyService taxPolicyService;
    private final TransactionTemplate transactionTemplate;
    private final TaxCategoryRepository taxCategoryRepository;
    private final TaxDocumentRepository taxDocumentRepository;
    private final TaxCalculationRepository taxCalculationRepository;
    private final TaxTransactionRepository taxTransactionRepository;
    private final TaxRegistrationRepository taxRegistrationRepository;
    private final TaxCalculationLineRepository taxCalculationLineRepository;
    private final TaxDocumentSequenceRepository taxDocumentSequenceRepository;
    private final TaxCalculationComponentRepository taxCalculationComponentRepository;

    /** 交易 → 冻结计算 → 首个应税组件的注册与地区包 → 税类名称 → 已有单据。 */
    @Override
    public TaxDocumentIssueContext loadIssueContext(long transactionId) {
        TaxTransaction transaction = taxTransactionRepository.findById(transactionId);
        if (transaction == null) {
            return null;
        }
        TaxCalculationView calculation = calculation(transaction.getCalculationId());
        TaxCalculationComponent taxable = calculation.getComponents().stream()
            .filter(component -> component.getTreatment() == TaxTreatment.TAXABLE && component.getRegistrationId() != null)
            .findFirst().orElse(null);
        if (taxable == null) {
            return null;
        }
        TaxRegistration registration = taxRegistrationRepository.findById(taxable.getRegistrationId());
        ResolvedTaxPolicy policy = taxPolicyService.loadReferenced(taxable.getPolicyId());
        Map<String, String> categoryNames = taxCategoryRepository.findByCodes(calculation.getLines().stream()
            .map(TaxCalculationLine::getCategoryCode).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(TaxCategory::getCategoryCode, TaxCategory::getName));
        TaxDocument document = taxDocumentRepository.findByTransaction(transactionId, TaxDocumentKind.RECEIPT,
            TaxCodes.DOCUMENT_SCOPE_SALE);
        return new TaxDocumentIssueContext(transaction, calculation, registration, policy, categoryNames, document);
    }

    /** 锁序列、取号、插入 RESERVED 单据。 */
    @Override
    public TaxDocument reserveReceipt(TaxDocumentIssueContext context, Instant now) {
        TaxReceiptRule rule = context.getPolicy().rules().getReceipt();
        if (rule == null) {
            throw new ErrorException("地区包没有简化税票规则: " + context.getPolicy().policy().getId());
        }
        TaxRegistration registration = context.getRegistration();
        String seriesCode = rule.getSeriesPrefix() + "-"
            + now.atZone(ZoneId.of(registration.getLegalTimezone())).getYear();
        return transactionTemplate.execute(status -> {
            taxDocumentSequenceRepository.insertIfAbsent(newSequence(context, rule, seriesCode, now));
            TaxDocumentSequence sequence = taxDocumentSequenceRepository.findForUpdate(
                context.getTransaction().getTaxSubjectPartyId(), registration.getId(), seriesCode);
            String number = rule.formatNumber(seriesCode, taxDocumentSequenceRepository.allocate(sequence, now));
            TaxDocument document = newDocument(context, rule, seriesCode, number, now);
            taxDocumentRepository.insert(document);
            return document;
        });
    }

    /** RESERVED → ISSUED。 */
    @Override
    public void markIssued(TaxDocument document, String fileAddress, String digest, Instant now) {
        transactionTemplate.executeWithoutResult(status -> taxDocumentRepository.markIssued(document, fileAddress, digest,
            now));
    }

    /** 冻结计算全貌。 */
    TaxCalculationView calculation(long calculationId) {
        List<Long> ids = List.of(calculationId);
        return new TaxCalculationView(taxCalculationRepository.findById(calculationId),
            taxCalculationLineRepository.findByCalculationIds(ids), taxCalculationComponentRepository.findByCalculationIds(ids),
            List.of());
    }

    /** 新年度序列，从 1 开始。 */
    TaxDocumentSequence newSequence(TaxDocumentIssueContext context, TaxReceiptRule rule, String seriesCode, Instant now) {
        TaxDocumentSequence sequence = new TaxDocumentSequence();
        sequence.setId(idGenerator.nextId());
        sequence.setIssuerPartyId(context.getTransaction().getTaxSubjectPartyId());
        sequence.setRegistrationId(context.getRegistration().getId());
        sequence.setSeriesCode(seriesCode);
        sequence.setDocumentKind(TaxDocumentKind.RECEIPT);
        sequence.setNextNumber(1L);
        sequence.setNumberFormat(rule.getNumberFormat());
        sequence.setCreateTime(now);
        sequence.setUpdateTime(now);
        return sequence;
    }

    /** RESERVED 单据，票面快照在分号时冻结。 */
    TaxDocument newDocument(TaxDocumentIssueContext context, TaxReceiptRule rule, String seriesCode, String number,
        Instant now) {
        TaxDocument document = new TaxDocument();
        document.setId(idGenerator.nextId());
        document.setTransactionId(context.getTransaction().getId());
        document.setDocumentKind(TaxDocumentKind.RECEIPT);
        document.setDocumentScopeKey(TaxCodes.DOCUMENT_SCOPE_SALE);
        document.setIssuanceMode(IssuanceMode.SYSTEM);
        document.setIssuerPartyId(context.getTransaction().getTaxSubjectPartyId());
        document.setRegistrationId(context.getRegistration().getId());
        document.setSeriesCode(seriesCode);
        document.setDocumentNumber(number);
        document.setIssuedAt(now);
        document.setCurrency(context.getTransaction().getCurrency());
        document.setPolicyId(context.getPolicy().policy().getId());
        document.setTemplateCode(rule.getTemplateCode());
        document.setTemplateRevision(rule.getTemplateRevision());
        document.setContentSchemaVersion(TaxCodes.DOCUMENT_CONTENT_SCHEMA_VERSION);
        document.setDocumentContentJson(TaxReceiptContent.build(context, rule, number, now));
        document.setIssueStatus(TaxDocumentIssueStatus.RESERVED);
        document.setSubmissionStatus(DocumentSubmissionStatus.NOT_REQUIRED);
        document.setCreateTime(now);
        document.setUpdateTime(now);
        return document;
    }
}
