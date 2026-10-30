package com.paytech.tax.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.tax.domain.calculation.CalculateTaxContext;
import com.paytech.tax.domainservice.repository.TaxCalculationComponentRepository;
import com.paytech.tax.domainservice.repository.TaxCalculationLineRepository;
import com.paytech.tax.domainservice.repository.TaxCalculationRepository;
import com.paytech.tax.domainservice.repository.TaxEvidenceRepository;
import com.paytech.tax.domainservice.service.TaxCalculationService;

import lombok.AllArgsConstructor;

/** TaxCalculationService 实现：计算不可变，只有插入。 */
@Service
@AllArgsConstructor
public class TaxCalculationServiceImpl implements TaxCalculationService {
    private final TransactionTemplate transactionTemplate;
    private final TaxEvidenceRepository taxEvidenceRepository;
    private final TaxCalculationRepository taxCalculationRepository;
    private final TaxCalculationLineRepository taxCalculationLineRepository;
    private final TaxCalculationComponentRepository taxCalculationComponentRepository;

    /** 头先插入以由唯一键拦截并发的同一身份，随后插入行、组件与证据。 */
    @Override
    public void saveCalculation(CalculateTaxContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            taxCalculationRepository.insert(context.getCalculation());
            taxCalculationLineRepository.insertAll(context.getLines());
            taxCalculationComponentRepository.insertAll(context.getComponents());
            taxEvidenceRepository.insertAll(context.getEvidence());
        });
    }
}
