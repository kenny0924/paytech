package com.paytech.tax.domainservice.service.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.paytech.tax.core.dal.dataobject.TaxCalculation;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;
import com.paytech.tax.core.dal.dataobject.TaxDocument;
import com.paytech.tax.core.dal.dataobject.TaxEvidence;
import com.paytech.tax.core.dal.dataobject.TaxReportingEntry;
import com.paytech.tax.core.dal.dataobject.TaxTransaction;
import com.paytech.tax.domain.calculation.TaxCalculationView;
import com.paytech.tax.domain.view.TaxOrderView;
import com.paytech.tax.domainservice.repository.TaxCalculationComponentRepository;
import com.paytech.tax.domainservice.repository.TaxCalculationLineRepository;
import com.paytech.tax.domainservice.repository.TaxCalculationRepository;
import com.paytech.tax.domainservice.repository.TaxDocumentRepository;
import com.paytech.tax.domainservice.repository.TaxEvidenceRepository;
import com.paytech.tax.domainservice.repository.TaxMarketRepository;
import com.paytech.tax.domainservice.repository.TaxPeriodRepository;
import com.paytech.tax.domainservice.repository.TaxPolicyRepository;
import com.paytech.tax.domainservice.repository.TaxRegistrationRepository;
import com.paytech.tax.domainservice.repository.TaxReportingEntryRepository;
import com.paytech.tax.domainservice.repository.TaxTransactionRepository;
import com.paytech.tax.domainservice.service.TaxQueryService;

import lombok.AllArgsConstructor;

/**
 * TaxQueryService 实现
 * <li>计算、行、组件与证据不可变，多条 SQL 读到的是同一份已提交事实；正式交易、底稿与单据在支付后追加，相邻提交时点不同只影响展示进度，
 * 不需要一致性快照事务。</li>
 */
@Service
@AllArgsConstructor
public class TaxQueryServiceImpl implements TaxQueryService {
    private final TaxMarketRepository taxMarketRepository;
    private final TaxPeriodRepository taxPeriodRepository;
    private final TaxPolicyRepository taxPolicyRepository;
    private final TaxEvidenceRepository taxEvidenceRepository;
    private final TaxDocumentRepository taxDocumentRepository;
    private final TaxCalculationRepository taxCalculationRepository;
    private final TaxRegistrationRepository taxRegistrationRepository;
    private final TaxTransactionRepository taxTransactionRepository;
    private final TaxReportingEntryRepository taxReportingEntryRepository;
    private final TaxCalculationLineRepository taxCalculationLineRepository;
    private final TaxCalculationComponentRepository taxCalculationComponentRepository;

    /** 按唯一身份读取后组装。 */
    @Override
    public TaxCalculationView findCalculation(String sourceSystem, String sourceObjectType, long sourceObjectId,
        int sourceRevision, String taxScopeKey) {
        TaxCalculation calculation = taxCalculationRepository.findByIdentity(sourceSystem, sourceObjectType,
            sourceObjectId, sourceRevision, taxScopeKey);
        return calculation == null ? null : views(List.of(calculation)).get(0);
    }

    /** 按 ID 读取后组装。 */
    @Override
    public TaxCalculationView findCalculation(long calculationId) {
        TaxCalculation calculation = taxCalculationRepository.findById(calculationId);
        return calculation == null ? null : views(List.of(calculation)).get(0);
    }

    /** 订单全部计算，再按计算取正式交易、底稿、期间、单据与引用的注册、地区包。 */
    @Override
    public TaxOrderView findOrderTax(String orderNo) {
        List<TaxCalculation> calculations = taxCalculationRepository.findByObjectNo(orderNo);
        if (calculations.isEmpty()) {
            return null;
        }
        List<TaxCalculationView> views = views(calculations);
        List<TaxTransaction> transactions = taxTransactionRepository.findByCalculationIds(
            calculations.stream().map(TaxCalculation::getId).toList());
        List<Long> transactionIds = transactions.stream().map(TaxTransaction::getId).toList();
        List<TaxReportingEntry> entries = taxReportingEntryRepository.findByTransactionIds(transactionIds);
        List<TaxDocument> documents = taxDocumentRepository.findByTransactionIds(transactionIds);
        return new TaxOrderView(orderNo, views, transactions, entries,
            taxPeriodRepository.findByIds(ids(entries.stream().map(TaxReportingEntry::getPeriodId))),
            documents, taxRegistrationRepository.findByIds(registrationIds(views, entries, documents)),
            taxMarketRepository.findByIds(ids(views.stream().flatMap(view -> view.getComponents().stream())
                .map(TaxCalculationComponent::getMarketId))),
            taxPolicyRepository.findByIds(ids(views.stream().flatMap(view -> view.getComponents().stream())
                .map(TaxCalculationComponent::getPolicyId))));
    }

    /** 一组计算头组装为计算视图（行、组件、证据各一次批量读取）。 */
    List<TaxCalculationView> views(List<TaxCalculation> calculations) {
        List<Long> calculationIds = calculations.stream().map(TaxCalculation::getId).toList();
        List<TaxCalculationLine> lines = taxCalculationLineRepository.findByCalculationIds(calculationIds);
        List<TaxCalculationComponent> components = taxCalculationComponentRepository.findByCalculationIds(calculationIds);
        List<TaxEvidence> evidence = taxEvidenceRepository.findByCalculationIds(calculationIds);
        return calculations.stream().map(calculation -> new TaxCalculationView(calculation,
            lines.stream().filter(line -> line.getCalculationId().equals(calculation.getId())).toList(),
            components.stream().filter(component -> component.getCalculationId().equals(calculation.getId())).toList(),
            evidence.stream().filter(item -> item.getCalculationId().equals(calculation.getId())).toList())).toList();
    }

    /** 组件、底稿与单据引用的注册 ID。 */
    static Set<Long> registrationIds(List<TaxCalculationView> views, List<TaxReportingEntry> entries,
        List<TaxDocument> documents) {
        Set<Long> ids = new HashSet<>(ids(views.stream().flatMap(view -> view.getComponents().stream())
            .map(TaxCalculationComponent::getRegistrationId)));
        ids.addAll(ids(entries.stream().map(TaxReportingEntry::getRegistrationId)));
        ids.addAll(ids(documents.stream().map(TaxDocument::getRegistrationId)));
        return ids;
    }

    /** 去空去重。 */
    static Collection<Long> ids(Stream<Long> ids) {
        return ids.filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
